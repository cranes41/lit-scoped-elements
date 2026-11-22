package io.github.litscoped

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor

/**
 * Adds the template tags using a `scopedElements` key to the references of that key, so Find Usages (Alt+F7)
 * lists them and renaming the key renames them.
 *
 * The tags are text inside template literals, which don't hold contributed references in WebStorm, so they
 * are provided by this searcher instead.
 */
class ScopedTagReferenceSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {

    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        val property = parameters.elementToSearch as? JSProperty ?: return
        val tagName = property.name ?: return
        if (!ScopedElements.isCustomElementName(tagName)) return
        val literal = property.parent as? JSObjectLiteralExpression ?: return
        if (!ScopedElements.isScopedElementsObject(literal)) return

        // A false from the consumer (e.g. a search for any one usage) stops the search.
        TagUsages.forEach(property, tagName, parameters.effectiveSearchScope) { template, tag ->
            consumer.process(ScopedTagReference(template, tag.range, tag.name))
        }
    }
}
