package io.github.litscoped

import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSFunctionExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * Finds the component classes whose `scopedElements` apply to a template.
 *
 * - A template written inside a class (render(), helper methods, arrow callbacks) belongs to that class.
 * - A template written in a function or variable outside any class ("sub render function") belongs to every
 *   class that uses it, directly or through other functions, in any file of the project:
 *
 * ```js
 * export const renderItem = (item) => html`<ui-icon></ui-icon>`;     // owner: MyList
 * function renderList(items) { return html`${items.map(renderItem)}`; } // owner: MyList
 * class MyList extends ScopedElementsMixin(LitElement) {
 *   render() { return renderList(this.items); }
 * }
 * ```
 */
object TemplateOwners {
    private const val MAX_DEPTH = 8

    fun findOwnerClasses(template: JSStringTemplateExpression): List<JSClass> {
        PsiTreeUtil.getParentOfType(template, JSClass::class.java)?.let { return listOf(it) }
        return CachedValuesManager.getCachedValue(template) {
            CachedValueProvider.Result.create(compute(template, HashSet(), 0).toList(), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    private fun compute(element: PsiElement, visited: MutableSet<PsiElement>, depth: Int): Set<JSClass> {
        PsiTreeUtil.getParentOfType(element, JSClass::class.java, false)?.let { return setOf(it) }
        if (depth > MAX_DEPTH) return emptySet()
        val holder = findNamedHolder(element) ?: return emptySet()
        if (!visited.add(holder)) return emptySet()

        val result = LinkedHashSet<JSClass>()
        for (reference in ReferencesSearch.search(holder, GlobalSearchScope.projectScope(holder.project)).findAll()) {
            result += compute(reference.element, visited, depth + 1)
        }
        return result
    }

    /**
     * The closest named declaration that contains [element] and can be referenced elsewhere:
     * a function declaration (`function renderX() {}`) or a variable (`const renderX = () => html`...``,
     * `const footer = html`...``). Anonymous callbacks (`items.map(i => html`...`)`) are skipped.
     */
    private fun findNamedHolder(element: PsiElement): PsiNamedElement? {
        var current = element.parent
        while (current != null && current !is PsiFile) {
            if (current is JSVariable && current.name != null) return current
            if (current is JSFunction && current !is JSFunctionExpression && current.name != null) return current
            current = current.parent
        }
        return null
    }
}
