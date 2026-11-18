package io.github.litscoped

import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.util.PsiTreeUtil

/** Finds the element class to register for a tag name. */
object ElementClassFinder {

    /**
     * The class for [tagName], looked up in order:
     * 1. the class registered for the tag in another `scopedElements`
     * 2. a global definition (`customElements.define` / `@customElement`)
     * 3. a class named after the tag (`my-card` → `MyCard`)
     */
    fun find(project: Project, tagName: String): JSClass? =
        ProjectSearch.cached(project, "class-for:$tagName") { listOfNotNull(compute(project, tagName)) }.firstOrNull()

    private fun compute(project: Project, tagName: String): JSClass? {
        registeredElsewhere(project, tagName)?.let { return it }
        GlobalElements.findDefinitions(project, tagName).firstNotNullOfOrNull { it.elementClass }?.let { return it }
        return byName(project, AddToScopedElementsFix.classNameFor(tagName))
    }

    private fun registeredElsewhere(project: Project, tagName: String): JSClass? {
        for (file in ProjectSearch.filesWithAnyKey(project, listOf(LitIndex.registration(tagName)))) {
            ProgressManager.checkCanceled()
            for (property in PsiTreeUtil.findChildrenOfType(file, JSProperty::class.java)) {
                if (property.name != tagName) continue
                val literal = property.parent as? JSObjectLiteralExpression ?: continue
                if (!ScopedElements.isScopedElementsObject(literal)) continue
                val cls = ScopedElements.resolveElementClass(ScopedElementEntry(tagName, property)) as? JSClass
                if (cls != null) return cls
            }
        }
        return null
    }

    private fun byName(project: Project, className: String): JSClass? {
        val psiManager = PsiManager.getInstance(project)
        var found: JSClass? = null
        PsiSearchHelper.getInstance(project).processCandidateFilesForText(
            ProjectSearch.scope(project), UsageSearchContext.IN_CODE, true, className,
        ) { virtualFile ->
            ProgressManager.checkCanceled()
            val file = psiManager.findFile(virtualFile) as? JSFile
            found = file?.let { f -> PsiTreeUtil.findChildrenOfType(f, JSClass::class.java).firstOrNull { it.name == className } }
            found == null
        }
        return found
    }
}
