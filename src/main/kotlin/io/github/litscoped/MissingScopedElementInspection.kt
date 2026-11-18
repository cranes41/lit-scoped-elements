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
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.lang.javascript.psi.ecmal4.JSClass

/**
 * Reports custom elements used in a Lit `html` template that are not registered in the `scopedElements` of the
 * class rendering it: the enclosing class (render() and any other method), or, for templates written in a
 * function outside the class, every class that calls that function.
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
                // The classes rendering this template: the enclosing class, or every class calling the
                // sub render function that contains it.
                val owners = TemplateOwners.findOwnerClasses(element).filter(ScopedElements::usesScopedElements)
                if (owners.isEmpty()) return
                val enclosing = PsiTreeUtil.getParentOfType(element, JSClass::class.java)

                for (tag in ScopedElements.findCustomElementTags(element)) {
                    if (tag.closing || ignored.any { it.matches(tag.name) }) continue
                    val missingIn = owners.filter { tag.name !in ScopedElements.getScopedElements(it) }.sortedBy { it.name }
                    if (missingIn.isEmpty()) continue

                    val qualify = owners.size > 1 || missingIn.single() != enclosing
                    val fixes = missingIn.mapNotNull { cls ->
                        ScopedElements.findOwnObjectLiteral(cls)?.let { literal ->
                            AddToScopedElementsFix(tag.name, cls.name.takeIf { qualify }, SmartPointerManager.createPointer(literal))
                        }
                    }
                    val where = missingIn.joinToString(", ") { "${it.name ?: "the class"}.scopedElements" }
                    holder.registerProblem(
                        element,
                        tag.range,
                        "Custom element <${tag.name}> is not declared in $where",
                        *fixes.toTypedArray<LocalQuickFix>(),
                    )
                }
            }
        }
    }
}
