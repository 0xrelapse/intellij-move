package org.move.ide.docs

import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.move.lang.core.psi.*
import org.move.lang.core.psi.ext.addressValue
import org.move.lang.core.psi.mixins.isBuiltinFunc
import org.move.lang.core.types.HasType

class MoveDocumentationProvider : AbstractDocumentationProvider() {
    override fun getCustomDocumentationElement(
        editor: Editor,
        file: PsiFile,
        contextElement: PsiElement?,
        targetOffset: Int
    ): PsiElement? {
        if (contextElement is MoveNamedAddress) return contextElement
        return super.getCustomDocumentationElement(editor, file, contextElement, targetOffset)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val buffer = StringBuilder()
        when (element) {
            // TODO: add docs for both scopes
            is MoveNamedAddress -> return element.addressValue
            is MoveFunctionSignature -> generateFuncDoc(element, buffer)
            else -> {
                if (element !is HasType) return null
                val type = element.resolvedType(emptyMap()) ?: return null
                buffer += type.typeLabel(element)
            }
        }
        return if (buffer.isEmpty()) null else buffer.toString()
    }

    private fun generateFuncDoc(element: MoveFunctionSignature, buffer: StringBuilder) {
        definition(buffer) {
            element.signature(it)
        }
    }
}

fun MoveElement.signature(builder: StringBuilder) {
    val rawLines = when (this) {
        is MoveFunctionSignature -> {
            val buffer = StringBuilder()
            buffer.b { it += name }
            buffer += "()"
            returnType?.generateDocumentation(buffer)
            listOf(buffer.toString())
        }
        else -> emptyList()
    }
    rawLines.joinTo(builder, "<br>")
}

private fun PsiElement.generateDocumentation(buffer: StringBuilder, prefix: String = "", suffix: String = "") {
    buffer += prefix
    when (this) {
        is MoveType -> {
            buffer += this.resolvedType(emptyMap())?.typeLabel(this) ?: "<unknown>"
        }
        is MoveReturnType -> this.type?.generateDocumentation(buffer, ": ")
    }
    buffer += suffix
}

private inline fun definition(buffer: StringBuilder, block: (StringBuilder) -> Unit) {
    buffer += DocumentationMarkup.DEFINITION_START
    block(buffer)
    buffer += DocumentationMarkup.DEFINITION_END
}

private operator fun StringBuilder.plusAssign(value: String?) {
    if (value != null) {
        append(value)
    }
}

private inline fun StringBuilder.b(action: (StringBuilder) -> Unit) {
    append("<b>")
    action(this)
    append("</b>")
}
