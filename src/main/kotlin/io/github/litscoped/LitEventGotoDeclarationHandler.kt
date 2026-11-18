package io.github.litscoped

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute

/**
 * - Cmd/Ctrl+Click on `@my-event` in a Lit template → where the event is fired (`new CustomEvent('my-event')`).
 * - Cmd/Ctrl+Click on `'my-event'` in `new CustomEvent('my-event')` → who listens to it
 *   (`@my-event=` bindings and `addEventListener('my-event')`).
 * - Cmd/Ctrl+Click on `'my-event'` in `addEventListener('my-event')` → where the event is fired.
 */
class LitEventGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        if (sourceElement == null) return null
        val project = sourceElement.project

        findBinding(sourceElement, offset)?.let { (binding, template) ->
            val tagClass = ScopedElements.resolveTagClass(template, binding.tagName)
            return LitEvents.findDispatches(project, binding.eventName, tagClass).toTargets()
        }

        val literal = sourceElement.parent as? JSLiteralExpression ?: return null
        LitEvents.dispatchedEventName(literal)?.let { return LitEvents.findListeners(project, it).toTargets() }
        LitEvents.listenedEventName(literal)?.let { return LitEvents.findDispatches(project, it).toTargets() }
        return null
    }

    private fun List<PsiElement>.toTargets(): Array<PsiElement>? = if (isEmpty()) null else distinct().toTypedArray()

    /** The `@event` binding under the caret, either in injected HTML or directly in the template literal. */
    private fun findBinding(element: PsiElement, offset: Int): Pair<EventBinding, JSStringTemplateExpression>? {
        val injectedManager = InjectedLanguageManager.getInstance(element.project)
        val host = injectedManager.getInjectionHost(element)
        if (host != null) {
            val attribute = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java, false) ?: return null
            val nameElement = attribute.nameElement ?: return null
            if (!nameElement.textRange.contains(element.textRange)) return null
            val template = host as? JSStringTemplateExpression ?: return null
            if (!ScopedElements.isLitHtmlTemplate(template)) return null
            val hostOffset = injectedManager.injectedToHost(element, element.textRange.startOffset)
            val relative = hostOffset - template.textRange.startOffset
            val binding = LitEvents.findEventBindings(template)
                .firstOrNull { relative >= it.range.startOffset && relative <= it.range.endOffset }
                ?: return null
            return binding to template
        }

        val template = PsiTreeUtil.getParentOfType(element, JSStringTemplateExpression::class.java, false) ?: return null
        if (!ScopedElements.isLitHtmlTemplate(template)) return null
        val relative = offset - template.textRange.startOffset
        val binding = LitEvents.findEventBindings(template)
            .firstOrNull { relative >= it.range.startOffset && relative <= it.range.endOffset }
            ?: return null
        return binding to template
    }
}
