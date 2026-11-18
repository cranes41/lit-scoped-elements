package io.github.litscoped

import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * A navigation target pointing at an exact range of a Lit template (a tag or an `@event` attribute).
 *
 * Elements of the injected HTML can't be used as targets: navigating to them opens the host file at its start.
 * This element lives in the host file and navigates to the right offset; its presentation shows the tag and the
 * file in the "Choose target" popup.
 */
class TemplateLocation(
    private val template: JSStringTemplateExpression,
    private val rangeInTemplate: TextRange,
) : FakePsiElement() {

    override fun getParent(): PsiElement = template

    override fun getContainingFile(): PsiFile = template.containingFile

    override fun getTextRange(): TextRange = rangeInTemplate.shiftRight(template.textRange.startOffset)

    override fun getTextOffset(): Int = textRange.startOffset

    override fun getText(): String = rangeInTemplate.substring(template.text)

    override fun isValid(): Boolean = template.isValid

    override fun getName(): String = text

    /** The opening tag the range belongs to, e.g. `<child-el id="a" @item-selected=${…}>`. */
    override fun getPresentableText(): String {
        val literal = ScopedElements.literalText(template)
        val start = literal.lastIndexOf('<', rangeInTemplate.startOffset).coerceAtLeast(0)
        val end = literal.indexOf('>', rangeInTemplate.startOffset).let { if (it < 0) literal.length else it + 1 }
        val tag = template.text.substring(start, end).replace(Regex("""\s+"""), " ")
        return if (tag.length > 80) tag.take(77) + "…" else tag
    }

    override fun getLocationString(): String {
        val document = containingFile.viewProvider.document
        val line = document?.getLineNumber(textOffset)?.plus(1)
        return if (line != null) "${containingFile.name}:$line" else containingFile.name
    }

    override fun getIcon(open: Boolean): Icon? = containingFile.getIcon(0)

    override fun canNavigate(): Boolean = containingFile.virtualFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        val file = containingFile.virtualFile ?: return
        OpenFileDescriptor(project, file, textOffset).navigate(requestFocus)
    }

    override fun equals(other: Any?): Boolean =
        other is TemplateLocation && other.template == template && other.rangeInTemplate == rangeInTemplate

    override fun hashCode(): Int = template.hashCode() * 31 + rangeInTemplate.hashCode()
}
