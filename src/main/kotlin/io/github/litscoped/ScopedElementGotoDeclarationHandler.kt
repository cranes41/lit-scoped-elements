package io.github.litscoped

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType

/**
 * Ctrl/Cmd+Click (or Go to Declaration) on a custom element tag inside a Lit `html` template
 * navigates to the class registered for that tag in the enclosing class's `scopedElements`.
 */
class ScopedElementGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (sourceElement == null) return null
        val (tagName, template) = findTag(sourceElement, offset) ?: return null
        if (!ScopedElements.isLitHtmlTemplate(template)) return null
        val cls = ScopedElements.findOwnerClass(template) ?: return null
        val entry = ScopedElements.getScopedElements(cls)[tagName] ?: return null
        val target = ScopedElements.resolveElementClass(entry) ?: entry.property
        return arrayOf(target)
    }

    private fun findTag(element: PsiElement, offset: Int): Pair<String, JSStringTemplateExpression>? {
        // Case 1: the template is injected as HTML (WebStorm's default for html``), so the caret is on an XML token.
        val xmlTag = element.parent as? XmlTag
        val type = element.node?.elementType
        if (xmlTag != null && (type == XmlTokenType.XML_NAME || type == XmlTokenType.XML_TAG_NAME) && element.text == xmlTag.name) {
            val host = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element)
            val template = host as? JSStringTemplateExpression
                ?: PsiTreeUtil.getParentOfType(host, JSStringTemplateExpression::class.java, false)
                ?: return null
            return xmlTag.name to template
        }

        // Case 2: no injection, the caret is directly inside the JS template literal.
        val template = PsiTreeUtil.getParentOfType(element, JSStringTemplateExpression::class.java, false) ?: return null
        val relative = offset - template.textRange.startOffset
        val occurrence = ScopedElements.findCustomElementTags(template)
            .firstOrNull { relative >= it.range.startOffset && relative <= it.range.endOffset }
            ?: return null
        return occurrence.name to template
    }
}
