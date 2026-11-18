package io.github.litscoped

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType

/**
 * Completes tag names inside Lit `html` templates with the elements registered in the `scopedElements` of the
 * classes rendering the template (inherited and spread entries included).
 */
class ScopedTagCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val template = tagNameTemplate(position) ?: return

        val entries = TemplateOwners.findOwnerClasses(template)
            .flatMap { owner -> ScopedElements.getScopedElements(owner).values.flatten() }
        val byTag = entries.groupBy { it.tagName }
        for ((tagName, tagEntries) in byTag) {
            val classes = tagEntries.mapNotNull { (ScopedElements.resolveElementClass(it) as? JSClass)?.name }.distinct()
            val lookup = LookupElementBuilder.create(tagName)
                .withIcon(AllIcons.Nodes.Tag)
                .withTypeText(classes.joinToString(" | ").ifEmpty { "scopedElements" }, true)
                .withTailText("  scoped", true)
            result.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
        }
    }

    /** The Lit template when [position] is the name of an opening tag in its injected HTML. */
    private fun tagNameTemplate(position: PsiElement): JSStringTemplateExpression? {
        val tag = position.parent as? XmlTag ?: return null
        val type = position.node.elementType
        if (type != XmlTokenType.XML_NAME && type != XmlTokenType.XML_TAG_NAME) return null
        if (tag.firstChild?.nextSibling != position && tag.node.findChildByType(XmlTokenType.XML_NAME) != position.node) return null
        val host = InjectedLanguageManager.getInstance(position.project).getInjectionHost(position) as? JSStringTemplateExpression
            ?: return null
        return host.takeIf(ScopedElements::isLitHtmlTemplate)
    }
}
