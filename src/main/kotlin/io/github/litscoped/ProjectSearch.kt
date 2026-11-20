package io.github.litscoped

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.indexing.FileBasedIndex
import java.util.concurrent.ConcurrentHashMap

/** Index-backed lookup of JS/TS files, and a project-level cache for search results. */
object ProjectSearch {

    /** The project, plus libraries when [LitSettings.searchLibraries] is on. */
    fun scope(project: Project): GlobalSearchScope =
        if (LitSettings.getInstance(project).searchLibraries) GlobalSearchScope.allScope(project)
        else GlobalSearchScope.projectScope(project)

    /** JS/TS files in [scope] that have any of the [LitIndex] keys. */
    fun filesWithAnyKey(project: Project, keys: Collection<String>, scope: GlobalSearchScope = scope(project)): Collection<PsiFile> {
        val psiManager = PsiManager.getInstance(project)
        val files = LinkedHashSet<PsiFile>()
        FileBasedIndex.getInstance().processFilesContainingAnyKey(LitIndex.NAME, keys, scope, null, null) { virtualFile ->
            ProgressManager.checkCanceled()
            psiManager.findFile(virtualFile)?.let(files::add)
            true
        }
        return files
    }

    private val CACHE_KEY = Key.create<CachedValue<ConcurrentHashMap<String, Any>>>("io.github.litscoped.searchCache")

    /**
     * Caches the result of [compute] under [key] until the next PSI change in the project or settings change.
     * Results may hold PSI elements: they are dropped as soon as anything changes.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> cached(project: Project, key: String, compute: () -> T): T {
        val cache = CachedValuesManager.getManager(project).getCachedValue(project, CACHE_KEY, {
            CachedValueProvider.Result.create(
                ConcurrentHashMap<String, Any>(),
                PsiModificationTracker.MODIFICATION_COUNT,
                LitSettings.getInstance(project).tracker,
            )
        }, false)
        cache[key]?.let { return it as T }
        val value = compute()
        cache.putIfAbsent(key, value)
        return value
    }
}
