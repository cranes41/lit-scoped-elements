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
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

/**
 * Completes `@event` attribute names on an element in a Lit template with the events its class fires
 * (found in the class and its super classes, see [LitEvents.eventsFiredBy]).
 */
class ScopedEventCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val attribute = position.parent as? XmlAttribute ?: return
        if (attribute.nameElement != position) return
        val tag = attribute.parent as? XmlTag ?: return
        val template = InjectedLanguageManager.getInstance(position.project).getInjectionHost(position) as? JSStringTemplateExpression
            ?: return
        if (!ScopedElements.isLitHtmlTemplate(template)) return

        // What was typed so far, "@" included (the default prefix may stop at "@").
        val typed = position.text.take((parameters.offset - position.textRange.startOffset).coerceAtLeast(0))
        if (!typed.startsWith("@")) return

        val classes = ScopedElements.resolveTagTargets(template, tag.name).filterIsInstance<JSClass>()
        val events = classes.flatMap { cls -> LitEvents.eventsFiredBy(cls).map { it to cls.name } }
        if (events.isEmpty()) return

        val matching = result.withPrefixMatcher(typed)
        for ((event, className) in events.distinctBy { it.first }) {
            val lookup = LookupElementBuilder.create("@$event")
                .withIcon(AllIcons.Nodes.Lambda)
                .withTypeText(className ?: LitBundle.message("completion.type.event"), true)
                .withTailText("  fired by <${tag.name}>", true)
            matching.addElement(PrioritizedLookupElement.withPriority(lookup, 100.0))
        }
    }
}
