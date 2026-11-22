package io.github.litscoped

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.refactoring.JSDefaultRenameProcessor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.SearchScope

/**
 * Renaming a `scopedElements` key (`'ui-button'`) also renames the matching tags in the templates it applies to.
 *
 * WebStorm's JS rename collects references with its own search, which doesn't consult
 * [ScopedTagReferenceSearcher]; this processor keeps the JS behaviour and adds the template tags.
 *
 * A template can be rendered by several classes registering the tag with different entries (a base class and a
 * subclass overriding its `scopedElements`). Renaming the tag in it must rename all those entries, or one class
 * would lose its element: so the keys sharing a template with the renamed one are renamed too, transitively.
 */
class ScopedElementKeyRenameProcessor : JSDefaultRenameProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean = isScopedElementKey(element)

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        super.prepareRenaming(element, newName, allRenames, scope)
        for (key in linkedKeys(element as JSProperty)) allRenames.putIfAbsent(key, newName)
    }

    override fun findReferences(element: PsiElement, searchScope: SearchScope, searchInCommentsAndStrings: Boolean): Collection<PsiReference> {
        val references = LinkedHashSet(super.findReferences(element, searchScope, searchInCommentsAndStrings))
        val property = element as JSProperty
        val tagName = property.name!!
        val templates = LinkedHashMap<JSStringTemplateExpression, TagOccurrence>()
        TagUsages.forEach(property, tagName, searchScope) { template, tag -> templates.putIfAbsent(template, tag); true }
        templates.forEach { (template, firstTag) ->
            // The keys sharing this template are renamed together (see prepareRenaming), and each of them gets
            // here: only one adds the template, as renaming it twice would rewrite stale ranges.
            if (registeringKeys(template, tagName).first() == property) {
                references += ScopedTagTemplateRenameReference(template, firstTag.range, firstTag.name)
            }
        }
        return references
    }

    companion object {
        /** True for a key named like a custom element in a `scopedElements` object (see [ScopedElements.isScopedElementsObject]). */
        fun isScopedElementKey(element: PsiElement): Boolean {
            val property = element as? JSProperty ?: return false
            val tagName = property.name ?: return false
            val literal = property.parent as? JSObjectLiteralExpression ?: return false
            return ScopedElements.isCustomElementName(tagName) && ScopedElements.isScopedElementsObject(literal)
        }

        /**
         * The keys to rename with [key]: those registering the same tag for a class rendering a template [key]
         * applies to, then the same for theirs, until nothing is added. Includes [key].
         */
        fun linkedKeys(key: JSProperty): Set<JSProperty> {
            val tagName = key.name ?: return setOf(key)
            val result = linkedSetOf(key)
            val queue = ArrayDeque(listOf(key))
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val templates = LinkedHashSet<JSStringTemplateExpression>()
                TagUsages.forEach(current, tagName) { template, _ -> templates += template; true }
                for (template in templates) {
                    ProgressManager.checkCanceled()
                    for (other in registeringKeys(template, tagName)) if (result.add(other)) queue += other
                }
            }
            return result
        }

        /** The keys registering [tagName] for the classes rendering [template], in a stable order. */
        private fun registeringKeys(template: JSStringTemplateExpression, tagName: String): List<JSProperty> =
            TemplateOwners.findOwnerClasses(template)
                .flatMap { ScopedElements.getScopedElements(it)[tagName].orEmpty() }
                .map { it.property }
                .distinct()
                .sortedWith(compareBy({ it.containingFile.virtualFile?.path ?: it.containingFile.name }, { it.textOffset }))
    }
}
