package io.github.litscoped

import com.intellij.openapi.progress.ProgressManager
import com.intellij.lang.javascript.psi.JSArgumentList
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSExpression
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSNewExpression
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

    /** Evaluates an event name: a string literal, or a constant / static field initialized with one. */
    private fun evaluateName(expression: JSExpression?, depth: Int = 0): String? {
        if (expression == null || depth > MAX_DEPTH) return null
        return when (expression) {
            is JSLiteralExpression -> literalString(expression)
            is JSReferenceExpression -> when (val resolved = ScopedElements.resolveDeep(expression)) {
                is JSVariable -> evaluateName(resolved.initializer, depth + 1)
                is JSField -> evaluateName(resolved.initializer, depth + 1)
                else -> null
            }
            else -> null
        }
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
        return all.distinctBy { it.first }
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
     * Names of the events fired by code in [cls] or its super classes: dispatch calls (see [dispatchedEventName])
     * and instantiations of `Event` subclasses. Cached until the next PSI or settings change.
     */
    fun eventsFiredBy(cls: JSClass): Set<String> = CachedValuesManager.getCachedValue(cls) {
        val names = LinkedHashSet<String>()
        for (current in classHierarchy(cls)) {
            ProgressManager.checkCanceled()
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
