package io.github.litscoped

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.codeInspection.options.OptPane.stringList
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/**
 * Reports custom elements used in a Lit `html` template (render() and any other method of the class)
 * that are not registered in the class's `scopedElements`.
 *
 * Only classes that use scoped elements (declare `scopedElements` or apply `ScopedElementsMixin`,
 * directly or through a super class) are checked.
 */
class MissingScopedElementInspection : LocalInspectionTool() {

    /** Regular expressions for tag names that never need to be scoped (e.g. globally defined elements). */
    @JvmField
    var ignoredTags: MutableList<String> = mutableListOf()

    override fun getOptionsPane(): OptPane = pane(
        stringList("ignoredTags", "Ignored tag names (regular expressions, e.g. 'sl-.*'):"),
    )

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val ignored = ignoredTags.mapNotNull { runCatching { Regex(it) }.getOrNull() }
        return object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element !is JSStringTemplateExpression || !ScopedElements.isLitHtmlTemplate(element)) return
                val cls = ScopedElements.findOwnerClass(element) ?: return
                if (!ScopedElements.usesScopedElements(cls)) return

                val declared = ScopedElements.getScopedElements(cls)
                val canFix = ScopedElements.findOwnObjectLiteral(cls) != null
                for (tag in ScopedElements.findCustomElementTags(element)) {
                    if (tag.closing || tag.name in declared) continue
                    if (ignored.any { it.matches(tag.name) }) continue
                    val fixes: Array<LocalQuickFix> = if (canFix) arrayOf(AddToScopedElementsFix(tag.name)) else LocalQuickFix.EMPTY_ARRAY
                    holder.registerProblem(
                        element,
                        tag.range,
                        "Custom element <${tag.name}> is not declared in ${cls.name ?: "the class"}.scopedElements",
                        *fixes,
                    )
                }
            }
        }
    }
}
