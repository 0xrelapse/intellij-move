package org.move.ide.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.util.descendantsOfType
import org.move.ide.inspections.fixes.RemoveParameterFix
import org.move.ide.inspections.fixes.RenameFix
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.functionLike
import org.move.lang.core.psi.ext.owner

class MvUnusedVariableInspection : MvLocalInspectionTool() {
    override fun buildMvVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        object : MvVisitor() {
            override fun visitLetStmt(o: MvLetStmt) {
                val bindings = o.pat?.descendantsOfType<MvBindingPat>().orEmpty()
                for (binding in bindings) {
                    checkUnused(binding, "Unused variable")
                }
            }

            override fun visitFunctionParameter(o: MvFunctionParameter) {
                if (o.functionLike?.codeBlock == null) return
                val binding = o.bindingPat
                checkUnused(binding, "Unused function parameter")
            }

            private fun checkUnused(binding: MvBindingPat, description: String) {
                val bindingName = binding.name ?: return
                if (bindingName.startsWith("_")) return

                val references = binding.searchReferences()
                    // filter out #[test] attributes
                    .filter { it.element !is MvAttrItemArgument }
                if (references.none()) {
                    val fixes = when (binding.owner) {
                        is MvFunctionParameter -> arrayOf(
                            RenameFix(binding, "_$bindingName"),
                            RemoveParameterFix(binding, bindingName)
                        )
                        else -> arrayOf(RenameFix(binding, "_$bindingName"))
                    }
                    holder.registerProblem(
                        binding,
                        description,
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        *fixes
                    )
                }
            }
        }
}
