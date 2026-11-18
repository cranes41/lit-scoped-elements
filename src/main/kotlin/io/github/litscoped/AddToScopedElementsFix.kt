package io.github.litscoped

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSChangeUtil
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * Adds `'tag-name': ElementClass` to a `scopedElements` object literal, which may be in another file when the
 * template is rendered by a sub function. [className] is shown in the fix name when set.
 *
 * With [elementClass] (found by [ElementClassFinder]) its name is used and it is imported into the literal's
 * file; otherwise the class name is guessed from the tag.
 */
class AddToScopedElementsFix(
    private val tagName: String,
    private val className: String?,
    private val literalPointer: SmartPsiElementPointer<JSObjectLiteralExpression>,
    private val elementClass: SmartPsiElementPointer<JSClass>? = null,
) : PsiUpdateModCommandQuickFix() {

    override fun getFamilyName(): String = "Add to scopedElements"

    override fun getName(): String =
        if (className != null) "Add '$tagName' to $className.scopedElements" else "Add '$tagName' to scopedElements"

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
        val literal = literalPointer.element?.let(updater::getWritable) ?: return
        if (literal.findProperty(tagName) != null) return

        val cls = elementClass?.element
        val valueName = cls?.name ?: classNameFor(tagName)
        val property = JSChangeUtil.createObjectLiteralPropertyFromText("'$tagName': $valueName", literal)
        val last = literal.propertiesIncludingSpreads.lastOrNull()
        val added = if (last != null) literal.addAfter(property, last) else literal.addAfter(property, literal.firstChild)
        CodeStyleManager.getInstance(project).reformat(literal)
        if (cls != null) Imports.ensureImported(literal.containingFile, cls)

        // Select the class name so it can be replaced right away (only possible in the current file).
        val value = (added as? JSProperty)?.value
        if (value != null && value.containingFile == element.containingFile) updater.select(value)
    }

    companion object {
        /** `my-child-el` -> `MyChildEl` */
        fun classNameFor(tagName: String): String =
            tagName.split('-', '.', '_').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }
}
