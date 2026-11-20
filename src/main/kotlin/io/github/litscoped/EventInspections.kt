package io.github.litscoped

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/** Event names of the DOM platform: listening to them never needs a dispatch in the project. */
internal object NativeEvents {
    val NAMES: Set<String> = """
        abort afterprint animationcancel animationend animationiteration animationstart auxclick beforeinput
        beforematch beforeprint beforetoggle beforeunload blur cancel canplay canplaythrough change click close
        compositionend compositionstart compositionupdate contentvisibilityautostatechange contextlost
        contextmenu contextrestored copy cuechange cut dblclick DOMContentLoaded drag dragend dragenter dragleave
        dragover dragstart drop durationchange emptied ended error focus focusin focusout formdata
        fullscreenchange fullscreenerror gotpointercapture hashchange input invalid keydown keypress keyup load
        loadeddata loadedmetadata loadstart lostpointercapture message messageerror mousedown mouseenter
        mouseleave mousemove mouseout mouseover mouseup offline online pagehide pagereveal pageshow pageswap
        paste pause play playing pointercancel pointerdown pointerenter pointerleave pointermove pointerout
        pointerover pointerrawupdate pointerup popstate progress ratechange readystatechange reset resize scroll
        scrollend search securitypolicyviolation seeked seeking select selectionchange selectstart slotchange
        stalled storage submit suspend timeupdate toggle touchcancel touchend touchmove touchstart
        transitioncancel transitionend transitionrun transitionstart unload visibilitychange volumechange
        waiting wheel
    """.trim().split(Regex("""\s+""")).toSet()

    fun isNative(name: String) = name in NAMES
}

/**
 * Reports `@my-event` bindings in Lit templates when nothing in the project fires `my-event` (often a typo).
 * DOM events are never reported.
 */
class UnknownEventInspection : LocalInspectionTool() {

    /** Regular expressions for event names fired outside the project (e.g. by a library). */
    @JvmField
    var ignoredEvents: MutableList<String> = mutableListOf()

    override fun getOptionsPane(): OptPane = pane(
        stringList("ignoredEvents", LitBundle.message("inspection.unknown.event.option.ignored")),
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val ignored = ignoredEvents.mapNotNull { runCatching { Regex(it) }.getOrNull() }
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is JSStringTemplateExpression || !ScopedElements.isLitHtmlTemplate(element)) return
                for (binding in LitEvents.findEventBindings(element)) {
                    val name = binding.eventName
                    if (NativeEvents.isNative(name) || ignored.any { it.matches(name) }) continue
                    if (LitEvents.findDispatches(element.project, name).isNotEmpty()) continue
                    holder.registerProblem(
                        element,
                        LitBundle.message("inspection.unknown.event.message", name),
                        ProblemHighlightType.WEAK_WARNING,
                        binding.range,
                    )
                }
            }
        }
    }
}

/**
 * Reports events fired with `new CustomEvent('x')` (or another dispatch form) that nothing in the project listens
 * to. Off by default: components often fire events for consumers outside the project.
 */
class UnheardEventInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            val literal = element as? JSLiteralExpression ?: return
            val name = LitEvents.dispatchedEventName(literal) ?: return
            if (LitEvents.findListeners(element.project, name).isNotEmpty()) return
            holder.registerProblem(literal, LitBundle.message("inspection.unheard.event.message", name), ProblemHighlightType.WEAK_WARNING)
        }
    }
}
