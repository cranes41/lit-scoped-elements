package io.github.litscoped

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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** An `@event-name=${...}` binding in a Lit template. [range] is relative to the template and includes the `@`. */
data class EventBinding(val eventName: String, val range: TextRange, val tagName: String)

/**
 * Links Lit event bindings (`@my-event=${...}`, `addEventListener('my-event', ...)`) with the code that
 * fires them (`new CustomEvent('my-event')`, `new Event('my-event')`).
 */
object LitEvents {
    private val EVENT_CONSTRUCTORS = setOf("CustomEvent", "Event")
    private const val ADD_LISTENER = "addEventListener"
    private const val MAX_DEPTH = 8

    private val TAG_WITH_ATTRIBUTES = Regex("""<([a-zA-Z][\w.\-]*)([^<>]*)""")
    private val EVENT_ATTRIBUTE = Regex("""@([^\s=/>"'`]+)\s*=""")

    // ------------------------------------------------------------- templates

    fun findEventBindings(template: JSStringTemplateExpression): List<EventBinding> {
        val text = ScopedElements.literalText(template)
        val result = mutableListOf<EventBinding>()
        for (tag in TAG_WITH_ATTRIBUTES.findAll(text)) {
            val attributes = tag.groups[2]!!
            for (attribute in EVENT_ATTRIBUTE.findAll(attributes.value)) {
                val start = attributes.range.first + attribute.range.first
                val nameEnd = attributes.range.first + attribute.groups[1]!!.range.last + 1
                result += EventBinding(attribute.groupValues[1], TextRange(start, nameEnd), tag.groupValues[1])
            }
        }
        return result
    }

    // ------------------------------------------------------- JS expressions

    /** Event name when [literal] is the first argument of `new CustomEvent(...)` / `new Event(...)`. */
    fun dispatchedEventName(literal: JSLiteralExpression): String? {
        val call = callOfFirstArgument(literal) as? JSNewExpression ?: return null
        return if (calleeName(call) in EVENT_CONSTRUCTORS) literal.stringValue else null
    }

    /** Event name when [literal] is the first argument of `x.addEventListener(...)`. */
    fun listenedEventName(literal: JSLiteralExpression): String? {
        val call = callOfFirstArgument(literal) ?: return null
        return if (call !is JSNewExpression && calleeName(call) == ADD_LISTENER) literal.stringValue else null
    }

    private fun callOfFirstArgument(expression: JSExpression): JSCallExpression? {
        val arguments = expression.parent as? JSArgumentList ?: return null
        val call = arguments.parent as? JSCallExpression ?: return null
        return if (call.arguments.firstOrNull() == expression) call else null
    }

    private fun calleeName(call: JSCallExpression): String? = (call.methodExpression as? JSReferenceExpression)?.referencedName

    /** Evaluates an event name: a string literal, or a constant / static field initialized with one. */
    private fun evaluateName(expression: JSExpression?, depth: Int = 0): String? {
        if (expression == null || depth > MAX_DEPTH) return null
        return when (expression) {
            is JSLiteralExpression -> expression.stringValue
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
        val all = mutableListOf<Pair<PsiElement, JSClass?>>()
        for (file in ProjectSearch.candidateJsFiles(project, listOf(eventName) + EVENT_CONSTRUCTORS)) {
            for (call in PsiTreeUtil.findChildrenOfType(file, JSNewExpression::class.java)) {
                if (calleeName(call) !in EVENT_CONSTRUCTORS) continue
                val argument = call.arguments.firstOrNull() ?: continue
                if (evaluateName(argument) == eventName) {
                    all += argument to PsiTreeUtil.getParentOfType(call, JSClass::class.java)
                }
            }
        }
        if (listenerTagClasses.isNotEmpty()) {
            val hierarchy = listenerTagClasses.flatMapTo(HashSet(), ::classHierarchy)
            val own = all.filter { (_, cls) -> cls != null && cls in hierarchy }
            if (own.isNotEmpty()) return own.map { it.first }
        }
        return all.map { it.first }
    }

    /** Places that listen to [eventName]: `@event-name=` template bindings and `addEventListener(...)` calls. */
    fun findListeners(project: Project, eventName: String): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        for (file in ProjectSearch.candidateJsFiles(project, listOf(eventName, ADD_LISTENER))) {
            for (template in PsiTreeUtil.findChildrenOfType(file, JSStringTemplateExpression::class.java)) {
                if (!ScopedElements.isLitHtmlTemplate(template)) continue
                for (binding in findEventBindings(template)) {
                    if (binding.eventName != eventName) continue
                    result += TemplateLocation(template, binding.range)
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

    private fun classHierarchy(cls: JSClass): Set<JSClass> {
        val result = LinkedHashSet<JSClass>()
        val queue = ArrayDeque(listOf(cls))
        while (queue.isNotEmpty() && result.size < 32) {
            val current = queue.removeFirst()
            if (result.add(current)) queue.addAll(current.superClasses)
        }
        return result
    }
}
