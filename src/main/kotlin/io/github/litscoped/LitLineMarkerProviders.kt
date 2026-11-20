package io.github.litscoped

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import javax.swing.Icon

private fun marker(
    anchor: PsiElement,
    icon: Icon,
    targets: List<PsiElement>,
    tooltip: String,
    title: String,
    result: MutableCollection<in RelatedItemLineMarkerInfo<*>>,
) {
    if (targets.isEmpty()) return
    result += NavigationGutterIconBuilder.create(icon)
        .setTargets(targets)
        .setTooltipText(tooltip)
        .setPopupTitle(title)
        .createLineMarkerInfo(anchor)
}

/**
 * Gutter icons in JS/TS code:
 * - on a `scopedElements` key → templates using the tag
 * - on the event name of a dispatch (`new CustomEvent('x')`, helper call, `super('x')` of an Event subclass) → listeners
 * - on the event name of `addEventListener('x')` → dispatches
 */
class JsLitLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = LitBundle.message("gutter.js.name")

    override fun getIcon(): Icon = AllIcons.Nodes.Tag

    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        if (element !is LeafPsiElement) return
        val parent = element.parent

        // 'ui-button' key of scopedElements
        if (parent is JSProperty && parent.nameIdentifier == element) {
            val tagName = parent.name ?: return
            val literal = parent.parent as? JSObjectLiteralExpression ?: return
            if (!ScopedElements.isCustomElementName(tagName) || !ScopedElements.isScopedElementsObject(literal)) return
            val usages = ScopedElementUsagesGotoHandler.findTagUsages(parent, tagName)
            marker(element, AllIcons.Nodes.Tag, usages, LitBundle.message("gutter.tag.usages.tooltip", usages.size, tagName), LitBundle.message("gutter.tag.usages.title", tagName), result)
            return
        }

        // Event names in JS calls. Leaves of the literal only (not html`` templates, which hold no event name).
        val literal = parent as? JSLiteralExpression ?: return
        if (literal is JSStringTemplateExpression && literal.arguments.isNotEmpty()) return
        if (literal.firstChild != element) return
        LitEvents.dispatchedEventName(literal)?.let { name ->
            val listeners = LitEvents.findListeners(element.project, name)
            marker(element, AllIcons.Nodes.Lambda, listeners, LitBundle.message("gutter.listeners.tooltip", listeners.size, name), LitBundle.message("gutter.listeners.title", name), result)
            return
        }
        LitEvents.listenedEventName(literal)?.let { name ->
            val dispatches = LitEvents.findDispatches(element.project, name)
            marker(element, AllIcons.Nodes.Lambda, dispatches, LitBundle.message("gutter.dispatches.tooltip", dispatches.size, name), LitBundle.message("gutter.dispatches.title", name), result)
        }
    }
}

/** Gutter icon on `@event` attributes of Lit templates (in the injected HTML) → where the event is fired. */
class TemplateEventLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun getName(): String = LitBundle.message("gutter.template.name")

    override fun getIcon(): Icon = AllIcons.Nodes.Lambda

    override fun collectNavigationMarkers(element: PsiElement, result: MutableCollection<in RelatedItemLineMarkerInfo<*>>) {
        if (element !is LeafPsiElement) return
        val attribute = element.parent as? XmlAttribute ?: return
        if (attribute.nameElement != element || !attribute.name.startsWith("@")) return
        val tag = attribute.parent as? XmlTag ?: return
        val template = InjectedLanguageManager.getInstance(element.project).getInjectionHost(element) as? JSStringTemplateExpression
            ?: return
        if (!ScopedElements.isLitHtmlTemplate(template)) return

        val name = attribute.name.removePrefix("@")
        val classes = ScopedElements.resolveTagClasses(template, tag.name)
        val dispatches = LitEvents.findDispatches(element.project, name, classes)
        marker(element, AllIcons.Nodes.Lambda, dispatches, LitBundle.message("gutter.dispatches.tooltip", dispatches.size, name), LitBundle.message("gutter.dispatches.title", name), result)
    }
}

