package io.github.litscoped

import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSParenthesizedExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSReturnStatement
import com.intellij.lang.javascript.psi.JSSpreadExpression
import com.intellij.lang.javascript.psi.ecmal4.JSSuperExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValueProvider
import com.intellij.util.Processor
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/** One `'tag-name': ElementClass` entry of a `scopedElements` declaration. */
data class ScopedElementEntry(val tagName: String, val property: JSProperty) {
    val value: JSExpression? get() = property.value
}

/** A custom element tag found in a template, with its range relative to the template expression. */
data class TagOccurrence(val name: String, val range: TextRange, val closing: Boolean)

/**
 * Helpers for the Open WC `ScopedElementsMixin` convention:
 *
 * ```js
 * class MyEl extends ScopedElementsMixin(LitElement) {
 *   static scopedElements = { 'child-el': ChildEl };          // field form
 *   static get scopedElements() { return { 'child-el': ChildEl }; } // getter form
 *   render() { return html`<child-el></child-el>`; }
 * }
 * ```
 */
object ScopedElements {
    const val MEMBER_NAME = "scopedElements"
    private const val MIXIN_NAME = "ScopedElementsMixin"
    private const val MAX_DEPTH = 16
    private const val MAX_SPREAD_DEPTH = 4

    private val TEMPLATE_TAGS = setOf("html", "staticHtml")

    // Valid custom element names start with a lowercase ASCII letter and contain a hyphen.
    private const val CUSTOM_ELEMENT_NAME = """[a-z][a-z0-9._]*-[a-z0-9._\-]*"""
    private val CUSTOM_ELEMENT_NAME_REGEX = Regex(CUSTOM_ELEMENT_NAME)
    private val TAG_REGEX = Regex("""<(/?)($CUSTOM_ELEMENT_NAME)(?=[\s/>]|$)""")
    private val COMMENT_REGEX = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

    // ---------------------------------------------------------------- templates

    fun isCustomElementName(name: String): Boolean = CUSTOM_ELEMENT_NAME_REGEX.matches(name)

    /** True for `html`...``, `staticHtml`...`` and qualified forms such as `lit.html`...``. */
    fun isLitHtmlTemplate(template: JSStringTemplateExpression): Boolean {
        val tagged = template.parent as? ES6TaggedTemplateExpression ?: return false
        if (tagged.templateExpression != template) return false
        val tag = tagged.tag ?: return false
        val name = (tag as? JSReferenceExpression)?.referencedName ?: tag.text
        return name in TEMPLATE_TAGS
    }

    /**
     * Finds custom element tags in the literal parts of [template]. Interpolations (`${...}`) and
     * HTML comments are ignored. Ranges are relative to [template].
     */
    fun findCustomElementTags(template: JSStringTemplateExpression): List<TagOccurrence> {
        return TAG_REGEX.findAll(literalText(template)).map { match ->
            val group = match.groups[2]!!
            TagOccurrence(group.value, TextRange(group.range.first, group.range.last + 1), match.groupValues[1] == "/")
        }.toList()
    }

    /**
     * The text of [template] with interpolations (`${...}`) and HTML comments replaced by spaces,
     * so offsets are preserved and only the literal HTML remains.
     */
    fun literalText(template: JSStringTemplateExpression): String {
        val text = template.text
        val chars = CharArray(text.length) { ' ' }
        for (range in template.stringRanges) {
            val start = range.startOffset.coerceIn(0, text.length)
            val end = range.endOffset.coerceIn(start, text.length)
            for (i in start until end) chars[i] = text[i]
        }
        for (comment in COMMENT_REGEX.findAll(String(chars))) {
            for (i in comment.range) chars[i] = ' '
        }
        return String(chars)
    }

    /**
     * Navigation targets for [tagName] in [template]: the element class registered in the `scopedElements` of
     * every class that renders the template (see [TemplateOwners]), or the entry itself when unresolved.
     */
    fun resolveTagTargets(template: JSStringTemplateExpression, tagName: String): List<PsiElement> =
        TemplateOwners.findOwnerClasses(template)
            .flatMap { cls -> getScopedElements(cls)[tagName].orEmpty() }
            .map { resolveElementClass(it) ?: it.property }
            .distinct()

    /** The element classes registered for [tagName] by the classes that render [template]. */
    fun resolveTagClasses(template: JSStringTemplateExpression, tagName: String): List<JSClass> =
        resolveTagTargets(template, tagName).filterIsInstance<JSClass>()

    // ----------------------------------------------------------- declarations

    /** True when the class (or one of its supers) declares `scopedElements` or applies `ScopedElementsMixin`. */
    fun usesScopedElements(cls: JSClass): Boolean = CachedValuesManager.getCachedValue(cls) {
        CachedValueProvider.Result.create(computeUsesScopedElements(cls, HashSet()), PsiModificationTracker.MODIFICATION_COUNT)
    }

    private fun computeUsesScopedElements(cls: JSClass, visited: MutableSet<JSClass>): Boolean {
        if (!visited.add(cls)) return false
        if (findDeclarationExpressions(cls).isNotEmpty()) return true
        if (cls.extendsList?.text?.contains(MIXIN_NAME) == true) return true
        return cls.superClasses.any { computeUsesScopedElements(it, visited) }
    }

    /**
     * All tag names available in [cls], including the ones inherited or spread from other declarations.
     *
     * A tag usually maps to one entry. It maps to several when a `scopedElements` getter has several `return`
     * branches registering it: each branch is a possible registry, so all of its entries are kept.
     */
    fun getScopedElements(cls: JSClass): Map<String, List<ScopedElementEntry>> = CachedValuesManager.getCachedValue(cls) {
        CachedValueProvider.Result.create(collect(cls, HashSet(), 0), PsiModificationTracker.MODIFICATION_COUNT)
    }

    /**
     * True when [literal] defines scoped elements: it is the `scopedElements` declaration of its class (field or
     * getter return value), or an object held in a variable that is spread into such a declaration
     * (`const shared = {...}` + `...shared`), possibly through other spread objects.
     *
     * Cheap for unrelated objects: a reference search only runs for objects assigned to a variable.
     */
    fun isScopedElementsObject(literal: JSObjectLiteralExpression, depth: Int = 0): Boolean {
        if (depth > MAX_SPREAD_DEPTH) return false
        val cls = PsiTreeUtil.getParentOfType(literal, JSClass::class.java)
        if (cls != null && findDeclarationExpressions(cls).any { unwrap(it) == literal }) return true

        var holder = literal.parent
        while (holder is JSParenthesizedExpression) holder = holder.parent
        val variable = holder as? JSVariable ?: return false
        if (unwrap(variable.initializer) != literal) return false

        val scope = GlobalSearchScope.projectScope(literal.project)
        val spreadIntoScopedElements = !ReferencesSearch.search(variable, scope).forEach(Processor { reference ->
            val spread = reference.element.parent as? JSSpreadExpression
            val outer = spread?.parent as? JSObjectLiteralExpression
            outer == null || !isScopedElementsObject(outer, depth + 1) // false stops the search: found
        })
        return spreadIntoScopedElements
    }

    /** The object literal written directly in [cls]'s own `scopedElements` declaration, if any. */
    fun findOwnObjectLiteral(cls: JSClass): JSObjectLiteralExpression? =
        findDeclarationExpressions(cls).map(::unwrap).filterIsInstance<JSObjectLiteralExpression>().firstOrNull()

    /** Expressions that define `scopedElements` in [cls] itself: a field initializer or getter return values. */
    fun findDeclarationExpressions(cls: JSClass): List<JSExpression> {
        cls.findFieldByName(MEMBER_NAME)?.initializer?.let { return listOf(it) }
        val getter = cls.functions.firstOrNull { it.name == MEMBER_NAME && !it.isSetProperty } ?: return emptyList()
        return returnedExpressions(getter)
    }

    private fun returnedExpressions(function: JSFunction): List<JSExpression> {
        val body = function.block ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(body, JSReturnStatement::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, JSFunction::class.java) == function }
            .mapNotNull { it.expression }
    }

    private fun collect(cls: JSClass, visited: MutableSet<JSClass>, depth: Int): Map<String, List<ScopedElementEntry>> {
        if (depth > MAX_DEPTH || !visited.add(cls)) return emptyMap()
        val result = LinkedHashMap<String, List<ScopedElementEntry>>()
        val expressions = findDeclarationExpressions(cls)
        if (expressions.isEmpty()) {
            // Static members are inherited: no own declaration means the super class one applies.
            for (superClass in cls.superClasses) result.putAll(collect(superClass, visited, depth + 1))
        } else {
            // Each return branch of a getter is an alternative registry: merge them, keeping every entry.
            // Inside one expression, a later entry overrides an earlier one, as in JavaScript.
            for (expression in expressions) {
                val branch = LinkedHashMap<String, List<ScopedElementEntry>>()
                collectFromExpression(expression, cls, branch, visited, depth + 1)
                for ((tag, entries) in branch) result[tag] = (result[tag].orEmpty() + entries).distinct()
            }
        }
        return result
    }

    private fun collectFromExpression(
        expression: JSExpression?,
        owner: JSClass,
        out: MutableMap<String, List<ScopedElementEntry>>,
        visited: MutableSet<JSClass>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        when (val expr = unwrap(expression)) {
            is JSObjectLiteralExpression -> for (element in expr.propertiesIncludingSpreads) {
                when (element) {
                    is JSProperty -> element.name?.let { out[it] = listOf(ScopedElementEntry(it, element)) }
                    is JSSpreadExpression -> collectFromExpression(element.expression, owner, out, visited, depth + 1)
                }
            }

            is JSReferenceExpression -> {
                val qualifier = expr.qualifier
                if (expr.referencedName == MEMBER_NAME && qualifier != null) {
                    // ...super.scopedElements  /  ...OtherElement.scopedElements
                    val classes = when (qualifier) {
                        is JSSuperExpression -> owner.superClasses.toList()
                        is JSReferenceExpression -> listOfNotNull(resolveDeep(qualifier) as? JSClass)
                        else -> emptyList()
                    }
                    for (cls in classes) out.putAll(collect(cls, HashSet(visited), depth + 1))
                    if (classes.isNotEmpty()) return
                }
                // ...sharedElements, where `const sharedElements = { ... }`
                val resolved = resolveDeep(expr)
                if (resolved is JSVariable) collectFromExpression(resolved.initializer, owner, out, visited, depth + 1)
            }
        }
    }

    // ------------------------------------------------------------- navigation

    /** The element class registered for [entry], resolved through imports when possible. */
    fun resolveElementClass(entry: ScopedElementEntry): PsiElement? {
        val value = unwrap(entry.value) ?: return null
        return if (value is JSReferenceExpression) resolveDeep(value) else value
    }

    fun resolveDeep(reference: JSReferenceExpression): PsiElement? {
        var resolved = reference.resolve()
        if (resolved is ES6ImportedBinding) {
            resolved = resolved.findReferencedElements().firstOrNull() ?: resolved
        }
        return resolved
    }

    private fun unwrap(expression: JSExpression?): JSExpression? {
        var current = expression
        while (current is JSParenthesizedExpression) current = current.innerExpression
        return current
    }
}
