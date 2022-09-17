package org.move.lang.core.types.infer

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValuesManager.getProjectPsiDependentCache
import com.jetbrains.rd.util.concurrentMapOf
import org.move.ide.inspections.InspectionQuickFix
import org.move.ide.presentation.expectedBindingFormText
import org.move.ide.presentation.name
import org.move.ide.presentation.text
import org.move.lang.MvElementTypes.SEMICOLON
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.*
import org.move.lang.core.psi.mixins.ty
import org.move.lang.core.types.ty.*

private val TYPE_INFERENCE_KEY: Key<CachedValue<InferenceContext>> = Key.create("TYPE_INFERENCE_KEY")

fun MvElement.functionInferenceCtx(msl: Boolean = this.isMsl()): InferenceContext {
    return this.containingFunctionLike?.inferenceCtx(msl) ?: InferenceContext(msl)
}

fun MvFunctionLike.inferenceCtx(msl: Boolean): InferenceContext {
    return if (msl) {
        getProjectPsiDependentCache(this) {
            getInferenceContext(it, true)
        }
    } else {
        getProjectPsiDependentCache(this) {
            getInferenceContext(it, false)
        }
    }
}

private fun getInferenceContext(owner: MvFunctionLike, msl: Boolean): InferenceContext {
    val functionCtx = InferenceContext(msl)
    for (param in owner.parameterBindings) {
        functionCtx.bindingTypes[param] = param.inferredTy(functionCtx)
    }
    if (owner is MvFunction) {
        owner.codeBlock?.let { inferCodeBlockTy(it, functionCtx, owner.returnTy) }
    }
    return functionCtx
}

fun inferCodeBlockTy(block: MvCodeBlock, blockCtx: InferenceContext, expectedTy: Ty?): Ty {
    for (stmt in block.stmtList) {
        when (stmt) {
            is MvExprStmt -> inferExprTy(stmt.expr, blockCtx)
            is MvLetStmt -> {
//                val explicitTy = stmt.declaredTy
                val explicitTy = stmt.typeAnnotation?.type?.let { inferTypeTy(it, stmt.isMsl()) }
                val initializerTy = stmt.initializer?.expr?.let { inferExprTy(it, blockCtx, explicitTy) }
                val pat = stmt.pat ?: continue
                val patTy = inferPatTy(pat, blockCtx, explicitTy ?: initializerTy)
                collectBindings(pat, patTy, blockCtx)
            }
        }
        blockCtx.processConstraints()
        blockCtx.resolveTyVarsFromContext(blockCtx)
    }
    val tailExpr = block.expr
    if (tailExpr == null) {
        if (expectedTy != null && expectedTy !is TyUnit) {
            blockCtx.typeErrors.add(
                TypeError.TypeMismatch(
                    block.rightBrace ?: block,
                    expectedTy,
                    TyUnit
                )
            )
            return TyUnknown
        }
        return TyUnit
    } else {
        val tailExprTy = inferExprTy(tailExpr, blockCtx, expectedTy)
        blockCtx.processConstraints()
        blockCtx.resolveTyVarsFromContext(blockCtx)
        return tailExprTy
    }
}

fun instantiateItemTy(item: MvNameIdentifierOwner, msl: Boolean): Ty {
    return when (item) {
        is MvStruct -> {
            val typeVars = item.typeParameters.map { TyInfer.TyVar(TyTypeParameter(it)) }
            fun findTypeVar(parameter: MvTypeParameter): Ty {
                return typeVars.find { it.origin?.origin == parameter }!!
            }

            val fieldTys = mutableMapOf<String, Ty>()
            for (field in item.fields) {
                val fieldName = field.name ?: return TyUnknown
                val fieldTy = item
                    .fieldsMap[fieldName]
                    ?.declarationTy(msl)
                    ?.foldTyTypeParameterWith { findTypeVar(it.origin) }
                    ?: TyUnknown
                fieldTys[fieldName] = fieldTy
            }

            val typeArgs = item.typeParameters.map { findTypeVar(it) }
            TyStruct(item, typeVars, fieldTys, typeArgs)
        }

        is MvFunctionLike -> {
            val typeVars = item.typeParameters.map { TyInfer.TyVar(TyTypeParameter(it)) }
            fun findTypeVar(parameter: MvTypeParameter): Ty {
                return typeVars.find { it.origin?.origin == parameter }!!
            }

            val paramTypes = mutableListOf<Ty>()
            for (param in item.parameters) {
                val paramType = param.typeAnnotation?.type
                    ?.let { inferTypeTy(it, msl) }
                    ?.foldTyTypeParameterWith { findTypeVar(it.origin) } ?: TyUnknown
                paramTypes.add(paramType)
            }
            val returnMvType = item.returnType?.type
            val retTy = if (returnMvType == null) {
                TyUnit
            } else {
                inferTypeTy(returnMvType, msl).foldTyTypeParameterWith { findTypeVar(it.origin) }
            }
            val acqTys = item.acquiresPathTypes.map {
                val acqItem =
                    it.path.reference?.resolve() as? MvNameIdentifierOwner ?: return@map TyUnknown
                instantiateItemTy(acqItem, msl)
                    .foldTyTypeParameterWith { tp -> findTypeVar(tp.origin) }
            }
            val typeArgs = item.typeParameters.map { findTypeVar(it) }
            TyFunction(item, typeVars, paramTypes, retTy, acqTys, typeArgs)
        }

        is MvTypeParameter -> item.ty()
        else -> TyUnknown
    }
}

fun isCompatibleReferences(expectedTy: TyReference, inferredTy: TyReference, msl: Boolean): Boolean {
    return isCompatible(expectedTy.referenced, inferredTy.referenced, msl)
}

fun isCompatibleStructs(expectedTy: TyStruct, inferredTy: TyStruct, msl: Boolean): Boolean {
    return expectedTy.item.fqName == inferredTy.item.fqName
            && expectedTy.typeArgs.size == inferredTy.typeArgs.size
            && expectedTy.typeArgs.zip(inferredTy.typeArgs).all { isCompatible(it.first, it.second, msl) }
}

fun isCompatibleTuples(expectedTy: TyTuple, inferredTy: TyTuple, msl: Boolean): Boolean {
    return expectedTy.types.size == inferredTy.types.size
            && expectedTy.types.zip(inferredTy.types).all { isCompatible(it.first, it.second, msl) }
}

fun isCompatibleIntegers(expectedTy: TyInteger, inferredTy: TyInteger): Boolean {
    return expectedTy.kind == TyInteger.DEFAULT_KIND
            || inferredTy.kind == TyInteger.DEFAULT_KIND
            || expectedTy.kind == inferredTy.kind
}

/// find common denominator for both types
fun combineTys(ty1: Ty, ty2: Ty, msl: Boolean): Ty {
    if (!isCompatible(ty1, ty2, msl) && !isCompatible(ty2, ty1, msl)) return TyUnknown
    return when {
        ty1 is TyReference && ty2 is TyReference
                && isCompatible(ty1.referenced, ty2.referenced, msl) -> {
            val combined = ty1.permissions.intersect(ty2.permissions)
            TyReference(ty1.referenced, combined, ty1.msl || ty2.msl)
        }

        else -> ty1
    }
}

fun isCompatible(rawExpectedTy: Ty, rawInferredTy: Ty, msl: Boolean = true): Boolean {
    val expectedTy = rawExpectedTy.mslTy()
    val inferredTy = rawInferredTy.mslTy()
    return when {
        expectedTy is TyNever || inferredTy is TyNever -> true
        expectedTy is TyUnknown || inferredTy is TyUnknown -> true
        expectedTy is TyInfer.TyVar -> {
            val compat = isCompatibleAbilities(expectedTy, inferredTy, msl)
            compat == Compat.Yes
        }
        /* expectedTy !is TyInfer.TyVar && */ inferredTy is TyInfer.TyVar -> {
            // todo: should always be false
            // todo: can it ever occur anyway?
            true
        }
        expectedTy is TyInfer.IntVar && (inferredTy is TyInfer.IntVar || inferredTy is TyInteger) -> {
            true
        }

        inferredTy is TyInfer.IntVar && expectedTy is TyInteger -> {
            true
        }

        expectedTy is TyTypeParameter || inferredTy is TyTypeParameter -> {
            // check abilities
            true
        }

        expectedTy is TyUnit && inferredTy is TyUnit -> true
        expectedTy is TyInteger && inferredTy is TyInteger -> isCompatibleIntegers(expectedTy, inferredTy)
        expectedTy is TyPrimitive && inferredTy is TyPrimitive
                && expectedTy.name == inferredTy.name -> true

        expectedTy is TyVector && inferredTy is TyVector
                && isCompatible(expectedTy.item, inferredTy.item, msl) -> true

        expectedTy is TyReference && inferredTy is TyReference
                // inferredTy permissions should be a superset of expectedTy permissions
                && (expectedTy.permissions - inferredTy.permissions).isEmpty() ->
            isCompatibleReferences(expectedTy, inferredTy, msl)

        expectedTy is TyStruct && inferredTy is TyStruct -> isCompatibleStructs(expectedTy, inferredTy, msl)
        expectedTy is TyTuple && inferredTy is TyTuple -> isCompatibleTuples(expectedTy, inferredTy, msl)
        else -> false
    }
}

sealed class Compat {
    object Yes : Compat()
    data class AbilitiesMismatch(val abilities: Set<Ability>) : Compat()
}

fun isCompatibleAbilities(expectedTy: Ty, actualTy: Ty, msl: Boolean): Compat {
    // skip ability check for specs
    if (msl) return Compat.Yes
    val missingAbilities = expectedTy.abilities() - actualTy.abilities()
    if (missingAbilities.isNotEmpty()) {
        return Compat.AbilitiesMismatch(missingAbilities)
    } else {
        return Compat.Yes
    }
}

sealed class TypeError(open val element: PsiElement) {
    abstract fun message(): String
    open fun quickfix(): LocalQuickFix? = null

    data class TypeMismatch(
        override val element: PsiElement,
        val expectedTy: Ty,
        val actualTy: Ty
    ) : TypeError(element) {
        override fun message(): String {
            return when (element) {
                is MvReturnExpr -> "Invalid return type '${actualTy.name()}', expected '${expectedTy.name()}'"
                else -> "Incompatible type '${actualTy.name()}', expected '${expectedTy.name()}'"
            }
        }
    }

    data class AbilitiesMismatch(
        override val element: PsiElement,
        val ty: Ty,
        val abilities: Set<Ability>
    ) : TypeError(element) {
        override fun message(): String {
            return "The type '${ty.text()}' " +
                    "does not have required ability '${abilities.map { it.label() }.first()}'"
        }

        override fun quickfix(): LocalQuickFix? {
            if (abilities.size > 1) return null
            val abilityName = abilities.map { it.label() }.first()
            return object : InspectionQuickFix("Add '$abilityName' ability to ${ty.name()}") {
                override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
                    val pathType = descriptor.psiElement as? MvPathType ?: return
                    val struct = pathType.reference?.resolve() as? MvStruct ?: return
                    // sanity check
                    val existingAbilities = struct.abilities.map { it.text }
                    if (abilityName in existingAbilities) return

                    val newList = project.psiFactory.abilitiesList(existingAbilities + listOf(abilityName))
                    if (struct.abilitiesList != null) {
                        struct.abilitiesList?.replace(newList)
                    } else {
                        val anchor = when {
                            struct.structBlock != null -> struct.structBlock
                            struct.hasChild(SEMICOLON) -> struct.getChild(SEMICOLON)
                            else -> return
                        }
                        struct.addBefore(newList, anchor)
                    }
                    struct.abilitiesList?.replace(newList)
                }
            }
        }
    }

    data class UnsupportedBinaryOp(
        override val element: PsiElement,
        val ty: Ty,
        val op: String
    ) : TypeError(element) {
        override fun message(): String {
            return "Invalid argument to '$op': " +
                    "expected 'u8', 'u64', 'u128', but found '${ty.text()}'"
        }
    }

    data class InvalidUnpacking(
        override val element: PsiElement,
        val assignedTy: Ty,
    ) : TypeError(element) {
        override fun message(): String {
            return "Invalid unpacking. Expected ${assignedTy.expectedBindingFormText()}"
        }
    }
}

class InferenceContext(val msl: Boolean) {
    var exprTypes = concurrentMapOf<MvExpr, Ty>()
    val patTypes = mutableMapOf<MvPat, Ty>()
    var callExprTypes = mutableMapOf<MvCallExpr, TyFunction>()
    val bindingTypes = concurrentMapOf<MvBindingPat, Ty>()
    var typeErrors = mutableListOf<TypeError>()

    val unificationTable = UnificationTable<TyInfer.TyVar, Ty>()
    val intUnificationTable = UnificationTable<TyInfer.IntVar, Ty>()

    private val solver = ConstraintSolver(this)

    fun addConstraint(ty1: Ty, ty2: Ty) {
        solver.registerConstraint(EqualityConstraint(ty1, ty2))
    }

    fun processConstraints(): Boolean {
        return solver.processConstraints()
    }

    fun cacheExprTy(expr: MvExpr, ty: Ty) {
        this.exprTypes[expr] = ty
    }

    fun cachePatTy(pat: MvPat, ty: Ty) {
        this.patTypes[pat] = ty
    }

    fun cacheCallExprTy(expr: MvCallExpr, ty: TyFunction) {
        this.callExprTypes[expr] = ty
    }

    fun resolveTyVarsFromContext(ctx: InferenceContext) {
        for ((expr, ty) in this.exprTypes.entries) {
            this.exprTypes[expr] = ctx.resolveTy(ty)
        }
        for ((binding, ty) in this.bindingTypes.entries) {
            this.bindingTypes[binding] = ctx.resolveTy(ty)
        }
    }

    fun resolveTy(ty: Ty): Ty {
        return ty.foldTyInferWith(this::resolveTyInfer)
    }

    fun resolveTyInfer(ty: Ty): Ty {
        if (ty !is TyInfer) return ty
        return when (ty) {
            is TyInfer.TyVar -> unificationTable.findValue(ty)?.let(this::resolveTyInfer) ?: ty
            is TyInfer.IntVar -> intUnificationTable.findValue(ty)?.let(this::resolveTyInfer) ?: ty
        }
    }

    fun childContext(): InferenceContext {
        val childContext = InferenceContext(this.msl)
        childContext.exprTypes = this.exprTypes
        childContext.callExprTypes = this.callExprTypes
        childContext.typeErrors = this.typeErrors
        return childContext
    }
}
