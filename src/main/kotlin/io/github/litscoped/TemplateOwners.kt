package io.github.litscoped

import com.intellij.openapi.progress.ProgressManager
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecmal4.JSSuperExpression
import com.intellij.lang.javascript.search.JSClassSearch
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
 * - A template written inside a class (render(), helper methods, arrow callbacks) belongs to that class, and to
 *   its subclasses that use the method holding it: subclasses that don't redefine the method, or whose override
 *   calls `super.method()`. Each of those renders the template with its own `scopedElements`.
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
    private const val MAX_SUBCLASSES = 64

    fun findOwnerClasses(template: JSStringTemplateExpression): List<JSClass> {
        return CachedValuesManager.getCachedValue(template) {
            CachedValueProvider.Result.create(compute(template, HashSet(), 0).toList(), PsiModificationTracker.MODIFICATION_COUNT)
        }
    }

    private fun compute(element: PsiElement, visited: MutableSet<PsiElement>, depth: Int): Set<JSClass> {
        PsiTreeUtil.getParentOfType(element, JSClass::class.java, false)?.let { return classAndInheritingSubclasses(it, element) }
        if (depth > MAX_DEPTH) return emptySet()
        val holder = findNamedHolder(element) ?: return emptySet()
        if (!visited.add(holder)) return emptySet()

        val result = LinkedHashSet<JSClass>()
        for (reference in ReferencesSearch.search(holder, GlobalSearchScope.projectScope(holder.project)).findAll()) {
            ProgressManager.checkCanceled()
            result += compute(reference.element, visited, depth + 1)
        }
        return result
    }

    /**
     * [cls] plus its subclasses (transitively, in the project) that run the class member containing [element]:
     * they don't declare a member with the same name, or their override calls `super.<name>`.
     */
    private fun classAndInheritingSubclasses(cls: JSClass, element: PsiElement): Set<JSClass> {
        val memberName = enclosingMember(cls, element)?.name ?: return setOf(cls)
        val result = linkedSetOf(cls)
        val queue = ArrayDeque(listOf(cls))
        val scope = GlobalSearchScope.projectScope(cls.project)
        while (queue.isNotEmpty() && result.size < MAX_SUBCLASSES) {
            ProgressManager.checkCanceled()
            val current = queue.removeFirst()
            for (subclass in directSubclasses(current, scope)) {
                if (subclass in result) continue
                val override = ownMember(subclass, memberName)
                if (override == null || callsSuper(override, memberName)) {
                    result += subclass
                    queue += subclass
                }
            }
        }
        return result
    }

    /**
     * Direct subclasses of [cls]: `extends Base` from the inheritance index, plus `extends SomeMixin(Base)` and
     * `const Mixed = SomeMixin(Base)` + `extends Mixed`, which the index can't see (it only records a class name
     * written after `extends`, not a call), found by searching the references to [cls].
     */
    private fun directSubclasses(cls: JSClass, scope: GlobalSearchScope): Set<JSClass> {
        val result = LinkedHashSet<JSClass>(JSClassSearch.searchClassInheritors(cls, false, scope).findAll())
        for (reference in ReferencesSearch.search(cls, scope).findAll()) {
            ScopedElements.classesExtendingThroughMixin(reference.element, scope).filterTo(result) { it != cls }
        }
        return result
    }

    /** The outermost method or field of [cls] containing [element] (arrow callbacks inside it are skipped). */
    private fun enclosingMember(cls: JSClass, element: PsiElement): PsiNamedElement? {
        var member: PsiNamedElement? = null
        var current: PsiElement? = element
        while (current != null && current != cls) {
            if (current is JSFunction || current is JSField) member = current as PsiNamedElement
            current = current.parent
        }
        return member
    }

    private fun ownMember(cls: JSClass, name: String): PsiElement? =
        cls.functions.firstOrNull { it.name == name && !it.isSetProperty } ?: cls.fields.firstOrNull { it.name == name }

    private fun callsSuper(member: PsiElement, name: String): Boolean =
        PsiTreeUtil.findChildrenOfType(member, JSReferenceExpression::class.java)
            .any { it.referenceName == name && it.qualifier is JSSuperExpression }

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
