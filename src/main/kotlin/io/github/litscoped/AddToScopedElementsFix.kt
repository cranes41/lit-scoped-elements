package io.github.litscoped

import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager

/** Adds `'tag-name': TagName` to the class's own `scopedElements` object literal. */
class AddToScopedElementsFix(private val tagName: String) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "Add to scopedElements"

    override fun getName(): String = "Add '$tagName' to scopedElements"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val cls = ScopedElements.findOwnerClass(element) ?: return
        val literal = ScopedElements.findOwnObjectLiteral(cls)?.let(updater::getWritable) ?: return
        if (literal.findProperty(tagName) != null) return

        val property = JSChangeUtil.createObjectLiteralPropertyFromText("'$tagName': ${classNameFor(tagName)}", literal)
        val last = literal.propertiesIncludingSpreads.lastOrNull()
        val added = if (last != null) literal.addAfter(property, last) else literal.addAfter(property, literal.firstChild)
        CodeStyleManager.getInstance(project).reformat(literal)

        // Select the class name so it can be replaced right away.
        (added as? JSProperty)?.value?.let { updater.select(it) }
    }

    companion object {
        /** `my-child-el` -> `MyChildEl` */
        fun classNameFor(tagName: String): String =
            tagName.split('-', '.', '_').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }
}
