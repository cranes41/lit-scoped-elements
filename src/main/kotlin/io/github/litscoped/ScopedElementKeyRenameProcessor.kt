package io.github.litscoped

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.refactoring.JSDefaultRenameProcessor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope

/**
 * Renaming a `scopedElements` key (`'ui-button'`) also renames the matching tags in the templates it applies to.
 *
 * WebStorm's JS rename collects references with its own search, which doesn't consult
 * [ScopedTagReferenceSearcher]; this processor keeps the JS behaviour and adds the template tags.
 */
class ScopedElementKeyRenameProcessor : JSDefaultRenameProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean {
        val property = element as? JSProperty ?: return false
        val tagName = property.name ?: return false
        val literal = property.parent as? JSObjectLiteralExpression ?: return false
        return ScopedElements.isCustomElementName(tagName) && ScopedElements.isScopedElementsObject(literal)
    }

    override fun findReferences(element: PsiElement, searchScope: SearchScope, searchInCommentsAndStrings: Boolean): Collection<PsiReference> {
        val references = LinkedHashSet(super.findReferences(element, searchScope, searchInCommentsAndStrings))
        val property = element as JSProperty
        val templates = LinkedHashMap<com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression, TagOccurrence>()
        TagUsages.forEach(property, property.name!!, searchScope) { template, tag -> templates.putIfAbsent(template, tag) }
        templates.forEach { (template, firstTag) ->
            references += ScopedTagTemplateRenameReference(template, firstTag.range, firstTag.name)
        }
        return references
    }
}
