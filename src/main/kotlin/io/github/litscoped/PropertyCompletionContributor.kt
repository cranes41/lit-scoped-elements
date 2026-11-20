package io.github.litscoped

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

/**
 * Completes attribute names on an element in a Lit template with the reactive properties of its class
 * (see [LitProperties]):
 * - `.name=${…}` property bindings, for every property
 * - `attribute=""` for the properties set from an attribute
 * - `?attribute=${…}` boolean attribute bindings, for `Boolean` properties
 *
 * Properties already bound on the element are not offered again.
 */
class PropertyCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val position = parameters.position
        val attribute = position.parent as? XmlAttribute ?: return
        if (attribute.nameElement != position) return
        val tag = attribute.parent as? XmlTag ?: return
        val template = InjectedLanguageManager.getInstance(position.project).getInjectionHost(position) as? JSStringTemplateExpression
            ?: return
        if (!ScopedElements.isLitHtmlTemplate(template)) return

        // What was typed so far, "." or "?" included (the default prefix stops before them).
        val typed = position.text.take((parameters.offset - position.textRange.startOffset).coerceAtLeast(0))
        if (typed.startsWith("@")) return

        val classes = ScopedElements.resolveTagTargets(template, tag.name).filterIsInstance<JSClass>()
        val properties = classes.flatMap(LitProperties::propertiesOf).distinctBy { it.name }
        if (properties.isEmpty()) return

        val bound = tag.attributes.filter { it != attribute }.map { it.name.trimStart('.', '?').lowercase() }.toSet()
        val matching = result.withPrefixMatcher(PlainPrefixMatcher(typed))
        for (property in properties) {
            if (property.name.lowercase() in bound || property.attribute in bound) continue
            matching.addElement(item(".${property.name}", property, binding = true))
            val attributeName = property.attribute ?: continue
            matching.addElement(item(attributeName, property, binding = false))
            if (property.isBoolean) matching.addElement(item("?$attributeName", property, binding = true))
        }
    }

    private fun item(text: String, property: LitProperty, binding: Boolean): LookupElement {
        val lookup = LookupElementBuilder.create(property.declaration, text)
            .withIcon(AllIcons.Nodes.Property)
            .withTypeText(property.type, true)
            .withTailText("  ${property.owner.name}", true)
            .withInsertHandler(if (binding) BINDING else ATTRIBUTE)
        lookup.putUserData(LitProperties.PROPERTY_ITEM, true)
        return PrioritizedLookupElement.withPriority(lookup, 90.0)
    }

    private companion object {
        /** Adds `=${}` with the caret inside the braces, unless a value follows already. */
        val BINDING = valueInserter("=\${}", 3)

        /** Adds `=""` with the caret inside the quotes, unless a value follows already. */
        val ATTRIBUTE = valueInserter("=\"\"", 2)

        fun valueInserter(text: String, caret: Int) = InsertHandler<LookupElement> { context, _ ->
            val offset = context.tailOffset
            if (context.document.charsSequence.getOrNull(offset) == '=') return@InsertHandler
            context.document.insertString(offset, text)
            context.editor.caretModel.moveToOffset(offset + caret)
        }
    }
}
