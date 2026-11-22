package io.github.litscoped

import com.intellij.openapi.project.Project
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.RenameInputValidatorEx
import com.intellij.util.ProcessingContext

/**
 * Refuses renaming a `scopedElements` key to a name that isn't a valid custom element name: the tags in the
 * templates are renamed with it, and the browser would reject the registration.
 */
class ScopedElementKeyRenameValidator : RenameInputValidatorEx {

    override fun getPattern(): ElementPattern<out PsiElement> =
        PlatformPatterns.psiElement().with(object : PatternCondition<PsiElement>("scopedElementKey") {
            override fun accepts(element: PsiElement, context: ProcessingContext?) =
                ScopedElementKeyRenameProcessor.isScopedElementKey(element)
        })

    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext): Boolean =
        ScopedElements.isCustomElementName(newName)

    override fun getErrorMessage(newName: String, project: Project): String? =
        if (ScopedElements.isCustomElementName(newName)) null else LitBundle.message("rename.invalid.tag.name", newName)
}
