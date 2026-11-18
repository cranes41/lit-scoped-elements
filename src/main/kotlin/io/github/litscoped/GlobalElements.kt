package io.github.litscoped

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.ES6Decorator
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/** A global custom element definition: [site] is the define call or decorator, [elementClass] the class it registers. */
data class GlobalDefinition(val tagName: String, val elementClass: JSClass?, val site: PsiElement) {
    /** Where to navigate: the class when resolved, the definition otherwise. */
    val target: PsiElement get() = elementClass ?: site
}

/**
 * Custom elements registered in the global registry:
 * - `customElements.define('x-el', XEl)` (also `window.customElements.define`)
 * - the Lit decorator `@customElement('x-el')` on a class
 */
object GlobalElements {
    private const val DEFINE = "define"
    private const val REGISTRY = "customElements"
    private const val DECORATOR = "customElement"

    fun findDefinitions(project: Project, tagName: String): List<GlobalDefinition> =
        ProjectSearch.cached(project, "global:$tagName") { computeDefinitions(project, tagName) }

    fun isDefinedGlobally(project: Project, tagName: String): Boolean = findDefinitions(project, tagName).isNotEmpty()

    private fun computeDefinitions(project: Project, tagName: String): List<GlobalDefinition> {
        val result = mutableListOf<GlobalDefinition>()
        for (file in ProjectSearch.filesWithAnyKey(project, listOf(LitIndex.string(tagName)))) {
            ProgressManager.checkCanceled()
            for (call in PsiTreeUtil.findChildrenOfType(file, JSCallExpression::class.java)) {
                definitionOf(call)?.takeIf { it.tagName == tagName }?.let(result::add)
            }
        }
        return result
    }

    /** The definition made by [call], if it is a `customElements.define(...)` call or a `@customElement(...)` decorator. */
    fun definitionOf(call: JSCallExpression): GlobalDefinition? {
        val callee = call.methodExpression as? JSReferenceExpression ?: return null
        val arguments = call.arguments
        val tagName = LitEvents.literalString(arguments.firstOrNull()) ?: return null
        if (!ScopedElements.isCustomElementName(tagName)) return null
        return when (callee.referencedName) {
            DEFINE -> {
                val qualifier = callee.qualifier as? JSReferenceExpression ?: return null
                if (qualifier.referencedName != REGISTRY) return null
                val elementClass = when (val value = arguments.getOrNull(1)) {
                    is JSReferenceExpression -> ScopedElements.resolveDeep(value) as? JSClass
                    is JSClass -> value
                    else -> null
                }
                GlobalDefinition(tagName, elementClass, call)
            }
            DECORATOR -> {
                if (PsiTreeUtil.getParentOfType(call, ES6Decorator::class.java) == null) return null
                GlobalDefinition(tagName, PsiTreeUtil.getParentOfType(call, JSClass::class.java), call)
            }
            else -> null
        }
    }
}
