package io.github.litscoped

import com.intellij.openapi.progress.ProgressManager
import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSNewExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSParenthesizedExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.TypeScriptSatisfiesExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptAsExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptPropertySignature
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.ecmal4.JSSuperExpression
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * An `@event-name=${...}` binding in a Lit template. [range] is relative to the template and includes the `@`;
 * [tagStart] is the offset of the `<` of the element it is attached to.
 */
data class EventBinding(val eventName: String, val range: TextRange, val tagName: String, val tagStart: Int)

/**
 * Links Lit event bindings (`@my-event=${...}`, `addEventListener('my-event', ...)`) with the code that
 * fires them:
 * - `new CustomEvent('my-event')`, `new Event('my-event')` (string, `` `template` `` or constant)
 * - helper calls configured in [LitSettings.eventHelpers], e.g. `this.fire('my-event')`
 * - `new SelectEvent()` where `class SelectEvent extends Event { constructor() { super('select') } }`
 *
 * Names can be literals or constants: variables, static fields, object properties (`Events.CHANGE`), enum members,
 * `as const` values. Events can also be declared without a visible dispatch, by a JSDoc `@fires my-event` (or
 * `@event`) tag on the element class, or by an entry of a TypeScript event map
 * (`interface HTMLElementEventMap { 'my-event': … }`).
 */
object LitEvents {
    private val EVENT_CONSTRUCTORS = setOf("CustomEvent", "Event")
    private const val ADD_LISTENER = "addEventListener"
    private const val MAX_DEPTH = 8

    // Attributes may contain quoted values with "<" or ">" in them.
    private val TAG_WITH_ATTRIBUTES = Regex("""<([a-zA-Z][\w.\-]*)((?:[^<>"']|"[^"]*"|'[^']*')*)""")
    private val QUOTED = Regex(""""[^"]*"|'[^']*'""")
    private val BINDINGS_KEY = Key.create<CachedValue<List<EventBinding>>>("lit.scoped.eventBindings")
    private val EVENT_SUPERCLASS = Regex("""\b(CustomEvent|Event)\b""")
    private val EVENT_ATTRIBUTE = Regex("""@([^\s=/>"'`]+)\s*=""")
    // `@fires my-event - text`, `@fires {CustomEvent<T>} my-event`, `@event my-event`
    private val DOC_EVENT = Regex("""@(?:fires|event)\s+(?:\{[^}\n]*}\s*)?([A-Za-z][\w:.\-]*)""")
    private val EVENT_MAP = Regex(""".*EventMap""")

    // ------------------------------------------------------------- templates

    /** `@event` bindings of [template], cached until the next PSI change. */
    fun findEventBindings(template: JSStringTemplateExpression): List<EventBinding> =
        CachedValuesManager.getCachedValue(template, BINDINGS_KEY) {
            CachedValueProvider.Result.create(computeEventBindings(template), PsiModificationTracker.MODIFICATION_COUNT)
        }

    /** Uncached [findEventBindings], for the indexer (which must not use PSI caches). */
    fun computeEventBindings(template: JSStringTemplateExpression): List<EventBinding> {
        val text = ScopedElements.literalText(template)
        val result = mutableListOf<EventBinding>()
        for (tag in TAG_WITH_ATTRIBUTES.findAll(text)) {
            val attributes = tag.groups[2]!!
            // Blank quoted values so `title="@x=1"` is not taken for a binding; offsets are preserved.
            val unquoted = QUOTED.replace(attributes.value) { " ".repeat(it.value.length) }
            for (attribute in EVENT_ATTRIBUTE.findAll(unquoted)) {
                val start = attributes.range.first + attribute.range.first
                val nameEnd = attributes.range.first + attribute.groups[1]!!.range.last + 1
                result += EventBinding(attribute.groupValues[1], TextRange(start, nameEnd), tag.groupValues[1], tag.range.first)
            }
        }
        return result
    }

    // ------------------------------------------------------- JS expressions

    /**
     * Event name when [literal] names a fired event: first argument of `new CustomEvent(...)` / `new Event(...)`,
     * of an event helper call, or of the `super(...)` call of an `Event` subclass.
     */
    fun dispatchedEventName(literal: JSLiteralExpression): String? {
        val call = callOfFirstArgument(literal) ?: return null
        return if (isDispatchCall(call)) literalString(literal) else null
    }

    /** Event name when [literal] is the first argument of `x.addEventListener(...)`. */
    fun listenedEventName(literal: JSLiteralExpression): String? {
        val call = callOfFirstArgument(literal) ?: return null
        return if (call !is JSNewExpression && calleeName(call) == ADD_LISTENER) literalString(literal) else null
    }

    /** True when [call]'s first argument is the name of an event it fires (see [dispatchedEventName]). */
    private fun isDispatchCall(call: JSCallExpression): Boolean = when {
        call is JSNewExpression -> calleeName(call) in EVENT_CONSTRUCTORS
        call.methodExpression is JSSuperExpression -> isEventClass(PsiTreeUtil.getParentOfType(call, JSClass::class.java))
        else -> calleeName(call) in LitSettings.getInstance(call.project).eventHelpers
    }

    /** True for a class extending `Event` or `CustomEvent`, directly or not. */
    private fun isEventClass(cls: JSClass?): Boolean {
        if (cls == null) return false
        return classHierarchy(cls).any { it.name in EVENT_CONSTRUCTORS || EVENT_SUPERCLASS.containsMatchIn(it.extendsList?.text.orEmpty()) }
    }

    private fun callOfFirstArgument(expression: JSExpression): JSCallExpression? {
        val arguments = expression.parent as? JSArgumentList ?: return null
        val call = arguments.parent as? JSCallExpression ?: return null
        return if (call.arguments.firstOrNull() == expression) call else null
    }

    private fun calleeName(call: JSCallExpression): String? = (call.methodExpression as? JSReferenceExpression)?.referencedName

    /** The value of a string literal, including a template literal without `${...}`. No resolving. */
    fun literalString(expression: JSExpression?): String? = when (expression) {
        is JSStringTemplateExpression ->
            if (expression.arguments.isEmpty()) expression.text.removeSurrounding("`") else null
        is JSLiteralExpression -> if (expression.isQuotedLiteral) expression.stringValue else null
        else -> null
    }

    /**
     * Evaluates an event name: a string literal, or a constant initialized with one (variable, static field,
     * enum member, object property such as `Events.CHANGE`), through `( )`, `as const` and `satisfies`.
     */
    private fun evaluateName(expression: JSExpression?, depth: Int = 0): String? {
        if (expression == null || depth > MAX_DEPTH) return null
        return when (expression) {
            is JSLiteralExpression -> literalString(expression)
            is JSParenthesizedExpression -> evaluateName(expression.innerExpression, depth + 1)
            is TypeScriptAsExpression -> evaluateName(expression.expression, depth + 1)
            is TypeScriptSatisfiesExpression -> evaluateName(expression.expression, depth + 1)
            is JSReferenceExpression -> when (val resolved = ScopedElements.resolveDeep(expression)) {
                is JSVariable -> evaluateName(resolved.initializer, depth + 1)
                is JSField -> evaluateName(resolved.initializer, depth + 1)
                is JSProperty -> evaluateName(resolved.value, depth + 1)
                // `Events.CHANGE` on an `as const` object resolves to a synthetic element: read the object instead.
                else -> propertyOf(expression, depth)?.let { evaluateName(it.value, depth + 1) }
            }
            else -> null
        }
    }

    /** The property named by the qualified [reference] (`Events.CHANGE`), found in its qualifier's object literal; null when unqualified. */
    private fun propertyOf(reference: JSReferenceExpression, depth: Int): JSProperty? {
        val name = reference.referenceName ?: return null
        val qualifier = reference.qualifier ?: return null
        return objectLiteral(qualifier, depth + 1)?.findProperty(name)
    }

    /** The object literal [expression] evaluates to: a constant initialized with one, or a property of one. */
    private fun objectLiteral(expression: JSExpression?, depth: Int): JSObjectLiteralExpression? {
        if (expression == null || depth > MAX_DEPTH) return null
        return when (expression) {
            is JSObjectLiteralExpression -> expression
            is JSParenthesizedExpression -> objectLiteral(expression.innerExpression, depth + 1)
            is TypeScriptAsExpression -> objectLiteral(expression.expression, depth + 1)
            is TypeScriptSatisfiesExpression -> objectLiteral(expression.expression, depth + 1)
            is JSReferenceExpression -> when (val resolved = ScopedElements.resolveDeep(expression)) {
                is JSVariable -> objectLiteral(resolved.initializer, depth + 1)
                is JSField -> objectLiteral(resolved.initializer, depth + 1)
                is JSProperty -> objectLiteral(resolved.value, depth + 1)
                else -> objectLiteral(propertyOf(expression, depth)?.value, depth + 1)
            }
            else -> null
        }
    }

    // ------------------------------------------------------- declarations

    /** An event named in a JSDoc `@fires` / `@event` tag: [range] is the name's range in [comment]. */
    class DocumentedEvent(val name: String, val comment: PsiComment, val range: TextRange)

    /** Events declared in the JSDoc comment of [cls] with `@fires` or `@event`. Reads text only: safe in the indexer. */
    fun documentedEvents(cls: JSClass): List<DocumentedEvent> = docComments(cls).flatMap { comment ->
        documentedEventNames(comment.text).map { (name, range) -> DocumentedEvent(name, comment, range) }
    }

    /** Names (and their ranges) of the events declared in a doc comment's [text]. */
    fun documentedEventNames(text: String): List<Pair<String, TextRange>> =
        if (!text.startsWith("/**")) emptyList()
        else DOC_EVENT.findAll(text).map { match ->
            val name = match.groups[1]!!
            name.value to TextRange(name.range.first, name.range.last + 1)
        }.toList()

    /** The doc comment of [cls]: inside the class element (as parsed) or right before it. */
    private fun docComments(cls: JSClass): List<PsiComment> {
        val result = PsiTreeUtil.getChildrenOfTypeAsList(cls, PsiComment::class.java).toMutableList()
        var previous = cls.prevSibling
        while (previous is PsiWhiteSpace) previous = previous.prevSibling
        if (previous is PsiComment) result += previous
        return result.filter { it.text.startsWith("/**") }
    }

    /** True for an entry of a TypeScript event map, as `'my-event'` in `interface HTMLElementEventMap { 'my-event': E }`. */
    fun isEventMapEntry(signature: TypeScriptPropertySignature): Boolean {
        val owner = PsiTreeUtil.getParentOfType(signature, TypeScriptInterface::class.java) ?: return false
        return owner.name?.let(EVENT_MAP::matches) == true
    }

    // ------------------------------------------------------------- searches

    /**
     * Places that fire [eventName]. With [listenerTagClasses] (the classes of the element the listener is
     * attached to), dispatches from those classes or their super classes are preferred; events can bubble from
     * any descendant, so all dispatches are returned when those classes fire nothing.
     */
    fun findDispatches(project: Project, eventName: String, listenerTagClasses: Collection<JSClass> = emptyList()): List<PsiElement> {
        val all = ProjectSearch.cached(project, "dispatches:$eventName") { computeDispatches(project, eventName) }
        if (listenerTagClasses.isNotEmpty()) {
            val hierarchy = listenerTagClasses.flatMapTo(HashSet(), ::classHierarchy)
            val own = all.filter { (_, cls) -> cls != null && cls in hierarchy }
            if (own.isNotEmpty()) return own.map { it.first }
        }
        return all.map { it.first }
    }

    private fun computeDispatches(project: Project, eventName: String): List<Pair<PsiElement, JSClass?>> {
        val all = mutableListOf<Pair<PsiElement, JSClass?>>()
        for (file in ProjectSearch.filesWithAnyKey(project, listOf(LitIndex.string(eventName), LitIndex.DYNAMIC))) {
            ProgressManager.checkCanceled()
            for (call in PsiTreeUtil.findChildrenOfType(file, JSCallExpression::class.java)) {
                if (!isDispatchCall(call)) continue
                val argument = call.arguments.firstOrNull() ?: continue
                if (evaluateName(argument) != eventName) continue
                if (call.methodExpression is JSSuperExpression) {
                    // Event subclass: the events are fired where the class is instantiated.
                    val eventClass = PsiTreeUtil.getParentOfType(call, JSClass::class.java)!!
                    val instantiations = instantiationsOf(eventClass)
                    if (instantiations.isEmpty()) all += argument to eventClass
                    else instantiations.forEach { all += it to PsiTreeUtil.getParentOfType(it, JSClass::class.java) }
                } else {
                    all += argument to PsiTreeUtil.getParentOfType(call, JSClass::class.java)
                }
            }
        }
        // `@fires` in the JSDoc of a class that fires nothing visible itself (a dispatch is a better target).
        val dispatchingClasses = all.mapNotNullTo(HashSet()) { it.second }
        for (file in ProjectSearch.filesWithAnyKey(project, listOf(LitIndex.string(eventName)))) {
            ProgressManager.checkCanceled()
            for (cls in PsiTreeUtil.findChildrenOfType(file, JSClass::class.java)) {
                if (cls in dispatchingClasses) continue
                for (documented in documentedEvents(cls)) {
                    if (documented.name != eventName) continue
                    all += TemplateLocation(documented.comment, documented.range, "@fires $eventName") to cls
                }
            }
        }
        // An event map entry only says the event exists: a target when nothing else is found.
        if (all.isEmpty()) all += eventMapEntries(project, eventName).map { it to null }
        return all.distinctBy { it.first }
    }

    /** Names of the entries of TypeScript event maps declaring [eventName]. */
    private fun eventMapEntries(project: Project, eventName: String): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        for (file in ProjectSearch.filesWithAnyKey(project, listOf(LitIndex.string(eventName)))) {
            for (signature in PsiTreeUtil.findChildrenOfType(file, TypeScriptPropertySignature::class.java)) {
                if (signature.name == eventName && isEventMapEntry(signature)) result += signature.nameIdentifier ?: signature
            }
        }
        return result
    }

    /** Places that listen to [eventName]: `@event-name=` template bindings and `addEventListener(...)` calls. */
    fun findListeners(project: Project, eventName: String): List<PsiElement> =
        ProjectSearch.cached(project, "listeners:$eventName") { computeListeners(project, eventName) }

    private fun computeListeners(project: Project, eventName: String): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        val keys = listOf(LitIndex.binding(eventName), LitIndex.string(eventName), LitIndex.DYNAMIC)
        for (file in ProjectSearch.filesWithAnyKey(project, keys)) {
            ProgressManager.checkCanceled()
            for (template in PsiTreeUtil.findChildrenOfType(file, JSStringTemplateExpression::class.java)) {
                if (!ScopedElements.isLitHtmlTemplate(template)) continue
                for (binding in findEventBindings(template)) {
                    if (binding.eventName != eventName) continue
                    result += TemplateLocation(template, binding.range, binding.tagStart)
                }
            }
            for (call in PsiTreeUtil.findChildrenOfType(file, JSCallExpression::class.java)) {
                if (call is JSNewExpression || calleeName(call) != ADD_LISTENER) continue
                val argument = call.arguments.firstOrNull() ?: continue
                if (evaluateName(argument) == eventName) result += argument
            }
        }
        return result
    }

    /**
     * Names of the events fired by code in [cls] or its super classes: dispatch calls (see [dispatchedEventName]),
     * instantiations of `Event` subclasses, and events declared with JSDoc `@fires`. Cached until the next PSI or settings change.
     */
    fun eventsFiredBy(cls: JSClass): Set<String> = CachedValuesManager.getCachedValue(cls) {
        val names = LinkedHashSet<String>()
        for (current in classHierarchy(cls)) {
            ProgressManager.checkCanceled()
            documentedEvents(current).mapTo(names) { it.name }
            for (call in PsiTreeUtil.findChildrenOfType(current, JSCallExpression::class.java)) {
                if (call.methodExpression is JSSuperExpression) continue
                if (isDispatchCall(call)) {
                    evaluateName(call.arguments.firstOrNull())?.let(names::add)
                } else if (call is JSNewExpression) {
                    val created = (call.methodExpression as? JSReferenceExpression)?.let(ScopedElements::resolveDeep) as? JSClass
                    if (created != null) eventSubclassName(created)?.let(names::add)
                }
            }
        }
        CachedValueProvider.Result.create(
            names as Set<String>,
            PsiModificationTracker.MODIFICATION_COUNT,
            LitSettings.getInstance(cls.project).tracker,
        )
    }

    /** The event name an `Event` subclass passes to `super(...)` in its constructor. */
    private fun eventSubclassName(eventClass: JSClass): String? {
        if (!isEventClass(eventClass)) return null
        val constructor = eventClass.constructor ?: return null
        val superCall = PsiTreeUtil.findChildrenOfType(constructor, JSCallExpression::class.java)
            .firstOrNull { it.methodExpression is JSSuperExpression } ?: return null
        return evaluateName(superCall.arguments.firstOrNull())
    }

    /** `new EventClass(...)` expressions in the search scope. */
    private fun instantiationsOf(eventClass: JSClass): List<JSNewExpression> =
        ReferencesSearch.search(eventClass, ProjectSearch.scope(eventClass.project)).findAll().mapNotNull { reference ->
            val newExpression = reference.element.parent as? JSNewExpression
            newExpression?.takeIf { it.methodExpression == reference.element }
        }

    private fun classHierarchy(cls: JSClass): Set<JSClass> {
        val result = LinkedHashSet<JSClass>()
        val queue = ArrayDeque(listOf(cls))
        while (queue.isNotEmpty() && result.size < 32) {
            val current = queue.removeFirst()
            if (result.add(current)) queue.addAll(ScopedElements.superClassesOf(current))
        }
        return result
    }
}
