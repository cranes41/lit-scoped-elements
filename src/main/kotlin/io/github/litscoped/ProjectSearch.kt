package io.github.litscoped

import com.intellij.openapi.progress.ProgressManager
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.search.UsageSearchContext

/** Index-backed lookup of project JS/TS files, shared by the tag and event searches. */
object ProjectSearch {

    /** JS/TS files of the project (libraries excluded) that contain any of [words], per the word index. */
    fun candidateJsFiles(project: Project, words: List<String>): Collection<PsiFile> {
        val scope = GlobalSearchScope.projectScope(project)
        val helper = PsiSearchHelper.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        val files = LinkedHashSet<PsiFile>()
        for (word in words.distinct()) {
            helper.processCandidateFilesForText(scope, UsageSearchContext.ANY, true, word) { virtualFile ->
                ProgressManager.checkCanceled()
                (psiManager.findFile(virtualFile) as? JSFile)?.let(files::add)
                true
            }
        }
        return files
    }
}
