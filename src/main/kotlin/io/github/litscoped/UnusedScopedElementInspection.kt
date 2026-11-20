package io.github.litscoped

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace

/**
 * Reports `scopedElements` entries whose tag is used in none of the templates they apply to (the class's,
 * its subclasses', those of classes spreading the object, and the sub render functions they call).
 */
class UnusedScopedElementInspection : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitElement(element: PsiElement) {
            val property = element as? JSProperty ?: return
            val tagName = property.name ?: return
            if (!ScopedElements.isCustomElementName(tagName)) return
            val literal = property.parent as? JSObjectLiteralExpression ?: return
            if (!ScopedElements.isScopedElementsObject(literal)) return
            if (ScopedElementUsagesGotoHandler.findTagUsages(property, tagName).isNotEmpty()) return

            holder.registerProblem(
                property.nameIdentifier ?: property,
                LitBundle.message("inspection.unused.message", tagName),
                ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                RemoveScopedElementFix(tagName),
            )
        }
    }
}

class RemoveScopedElementFix(private val tagName: String) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = LitBundle.message("fix.remove.family")

    override fun getName(): String = LitBundle.message("fix.remove.name", tagName)

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val property = element as? JSProperty ?: element.parent as? JSProperty ?: return
        // Remove the line break and indentation before the entry too, so no blank line is left.
        (property.prevSibling as? PsiWhiteSpace)?.delete()
        property.delete()
    }
}
