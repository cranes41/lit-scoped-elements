package io.github.litscoped

import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

/**
 * A navigation target pointing at an exact range of a Lit template (a tag or an `@event` attribute), or of another
 * element that can't be a target itself, such as the event name in a JSDoc `@fires my-event` tag.
 *
 * Elements of the injected HTML can't be used as targets: navigating to them opens the host file at its start.
 * This element lives in the host file and navigates to the right offset; its presentation shows the tag and the
 * file in the "Choose target" popup.
 *
 * The range is tracked with a smart pointer so it follows edits made while a popup or usage view is open, and
 * the presentation is computed once, up front, so rendering never reads stale offsets.
 *
 * @param container the element holding the range (the template)
 * @param rangeInContainer the range to navigate to, relative to [container]
 * @param presentableText what the "Choose target" popup shows
 */
class TemplateLocation(
    container: PsiElement,
    rangeInContainer: TextRange,
    private val presentableText: String,
) : FakePsiElement() {

    /** A range of a Lit template; [tagStartInTemplate] is the offset of the `<` of the element it belongs to. */
    constructor(template: JSStringTemplateExpression, rangeInTemplate: TextRange, tagStartInTemplate: Int) :
        this(template, rangeInTemplate, openingTag(template, tagStartInTemplate))

    private val file: PsiFile = container.containingFile
    private val initialRange: TextRange = rangeInContainer.shiftRight(container.textRange.startOffset)
    private val containerPointer = SmartPointerManager.createPointer(container)
    private val rangePointer = SmartPointerManager.getInstance(file.project).createSmartPsiFileRangePointer(file, initialRange)
    private val locationString: String = lineOf(initialRange.startOffset)?.let { "${file.name}:$it" } ?: file.name

    override fun getParent(): PsiElement = containerPointer.element ?: file

    override fun getContainingFile(): PsiFile = file

    override fun getTextRange(): TextRange = rangePointer.range?.let(TextRange::create) ?: initialRange

    override fun getTextOffset(): Int = textRange.startOffset

    override fun getText(): String {
        val contents = file.viewProvider.contents
        val range = textRange
        return if (range.endOffset <= contents.length) range.subSequence(contents).toString() else ""
    }

    override fun isValid(): Boolean = file.isValid && containerPointer.element != null && rangePointer.range != null

    override fun getName(): String = text

    override fun getPresentableText(): String = presentableText

    override fun getLocationString(): String = locationString

    override fun getIcon(open: Boolean): Icon? = file.getIcon(0)

    override fun canNavigate(): Boolean = file.virtualFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun navigate(requestFocus: Boolean) {
        val virtualFile = file.virtualFile ?: return
        OpenFileDescriptor(project, virtualFile, textOffset).navigate(requestFocus)
    }

    override fun equals(other: Any?): Boolean =
        other is TemplateLocation && other.file == file && other.initialRange == initialRange

    override fun hashCode(): Int = file.hashCode() * 31 + initialRange.hashCode()

    private fun lineOf(offset: Int): Int? {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
        return if (offset in 0..document.textLength) document.getLineNumber(offset) + 1 else null
    }

    companion object {
        /** The opening tag starting at [tagStart], e.g. `<child-el id="a" @item-selected=${…}>`, quotes respected. */
        private fun openingTag(template: JSStringTemplateExpression, tagStart: Int): String {
            val text = template.text
            if (tagStart !in text.indices) return ""
            val literal = ScopedElements.literalText(template)
            var end = text.length
            var quote: Char? = null
            for (i in tagStart until literal.length) {
                val c = literal[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '"' || c == '\'' -> quote = c
                    c == '>' -> { end = i + 1; break }
                }
            }
            val tag = text.substring(tagStart, end).replace(Regex("""\s+"""), " ")
            return if (tag.length > 80) tag.take(77) + "…" else tag
        }
    }
}
