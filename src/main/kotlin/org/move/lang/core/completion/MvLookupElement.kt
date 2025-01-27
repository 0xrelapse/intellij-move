package org.move.lang.core.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.inferredTy
import org.move.lang.core.types.infer.InferenceContext
import org.move.lang.core.types.infer.instantiateItemTy
import org.move.lang.core.types.infer.isCompatible
import org.move.lang.core.types.ty.Ty
import org.move.lang.core.types.ty.TyUnknown

fun LookupElement.toMvLookupElement(properties: LookupElementProperties): MvLookupElement =
    MvLookupElement(this, properties)

class MvLookupElement(
    delegate: LookupElement,
    val props: LookupElementProperties
) : LookupElementDecorator<LookupElement>(delegate) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as MvLookupElement

        if (props != other.props) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + props.hashCode()
        return result
    }
}

data class LookupElementProperties(
    /**
     * `true` if after insertion of the lookup element it will form an expression with a type
     * that conforms to the expected type of that expression.
     *
     * ```
     * fn foo() -> String { ... } // isReturnTypeConformsToExpectedType = true
     * fn bar() -> i32 { ... }    // isReturnTypeConformsToExpectedType = false
     * fn main() {
     *     let a: String = // <-- complete here
     * }
     * ```
     */
    val isReturnTypeConformsToExpectedType: Boolean = false,

    val isCompatibleWithContext: Boolean = false,

    val typeHasAllRequiredAbilities: Boolean = false,
)

fun lookupProperties(element: MvNamedElement, context: CompletionContext): LookupElementProperties {
    val ctx = InferenceContext(context.itemVis.isMsl)
//    val ctx = element.functionInferenceCtx(context.itemVis.isMsl)
    var props = LookupElementProperties()
    val expectedTy = context.expectedTy
    if (expectedTy != null) {
        val ty = element.asTy(ctx)
        props = props.copy(
            isReturnTypeConformsToExpectedType = isCompatible(context.expectedTy, ty, ctx.msl)
        )
    }
    return props
}

private fun MvNamedElement.asTy(ctx: InferenceContext): Ty =
    when (this) {
//        is RsFieldDecl -> typeReference?.type
        is MvFunction -> this.returnTypeTy(ctx)
        is MvStruct -> instantiateItemTy(this, ctx)
        is MvBindingPat -> this.inferredTy(ctx)
        else -> TyUnknown
    }
