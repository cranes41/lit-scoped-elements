package io.github.litscoped

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.util.TextRange

/**
 * Cmd/Ctrl+Click on a tag name key of `scopedElements` (`{ 'child-el': ChildEl }`) lists the `<child-el>`
 * usages in the templates this very entry applies to: templates of the declaring class, of subclasses inheriting
 * or spreading it, of classes spreading a shared object that contains it, and of the sub render functions
 * those classes call.
 */
class ScopedElementUsagesGotoHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (sourceElement == null) return null
        val property = PsiTreeUtil.getParentOfType(sourceElement, JSProperty::class.java, false) ?: return null
        if (property.parent !is JSObjectLiteralExpression) return null
        val nameElement = property.nameIdentifier ?: return null
        if (!nameElement.textRange.contains(sourceElement.textRange)) return null
        val tagName = property.name ?: return null
        if (!CUSTOM_ELEMENT_NAME.matches(tagName)) return null

        val usages = findTagUsages(property, tagName)
        return if (usages.isEmpty()) null else usages.toTypedArray()
    }

    companion object {
        private val CUSTOM_ELEMENT_NAME = Regex("""[a-z][a-z0-9._]*-[a-z0-9._\-]*""")

        /** Opening `<tagName>` tags in Lit templates whose `scopedElements` resolve [tagName] to [property]. */
        fun findTagUsages(property: JSProperty, tagName: String): List<PsiElement> {
            val project = property.project
            val result = mutableListOf<PsiElement>()
            for (file in LitEvents.candidateFiles(project, listOf(tagName))) {
                for (template in PsiTreeUtil.findChildrenOfType(file, JSStringTemplateExpression::class.java)) {
                    if (!ScopedElements.isLitHtmlTemplate(template)) continue
                    val tags = ScopedElements.findCustomElementTags(template).filter { !it.closing && it.name == tagName }
                    if (tags.isEmpty()) continue
                    val applies = TemplateOwners.findOwnerClasses(template)
                        .any { ScopedElements.getScopedElements(it)[tagName]?.property == property }
                    if (!applies) continue
                    for (tag in tags) {
                        // Range from "<" to the end of the tag name.
                        result += TemplateLocation(template, TextRange(tag.range.startOffset - 1, tag.range.endOffset))
                    }
                }
            }
            return result
        }
    }
}
