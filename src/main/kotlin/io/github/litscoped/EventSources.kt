package io.github.litscoped

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import java.io.File

/**
 * Files and folders listed in [LitSettings.eventSources]: places where events are fired, searched in addition to the
 * project, such as one package of `node_modules` or a folder outside the project.
 */
object EventSources {

    /**
     * The configured files and folders that exist and are acceptable (see [isAcceptable]).
     *
     * The settings file can be committed with a project, so its paths are not trusted blindly: only local files,
     * never a whole drive or home folder, and nothing outside the project until the project is trusted.
     */
    fun roots(project: Project): List<VirtualFile> {
        val base = project.guessProjectDir()
        val trusted = TrustedProjects.isProjectTrusted(project)
        return LitSettings.getInstance(project).eventSources
            .mapNotNull { resolve(project, it) }
            .filter { isAcceptable(it) && (trusted || (base != null && VfsUtilCore.isAncestor(base, it, false))) }
            .distinct()
    }

    /** [path] as a local file: a `file://` URL, an absolute path, or a path relative to the project folder. */
    fun resolve(project: Project, path: String): VirtualFile? {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return null
        if ("://" in trimmed) {
            val scheme = trimmed.substringBefore("://")
            return if (scheme in LOCAL_SCHEMES) VirtualFileManager.getInstance().findFileByUrl(trimmed) else null
        }
        if (File(trimmed).isAbsolute) return LocalFileSystem.getInstance().findFileByPath(trimmed)
        return project.guessProjectDir()?.findFileByRelativePath(trimmed)
    }

    /** False for roots too broad to index: a filesystem root, the home folder, or a folder containing it. */
    fun isAcceptable(file: VirtualFile): Boolean {
        if (file.parent == null) return false
        val home = System.getProperty("user.home")?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return true
        return !VfsUtilCore.isAncestor(file, home, false)
    }

    /** `file` for real files; `temp` is the in-memory file system of tests. */
    private val LOCAL_SCHEMES = setOf("file", "temp")

    /** How a chosen file is stored: relative to the project folder when inside it, absolute otherwise. */
    fun pathOf(project: Project, file: VirtualFile): String {
        val base = project.guessProjectDir()
        if (base != null && VfsUtilCore.isAncestor(base, file, false)) return VfsUtilCore.getRelativePath(file, base) ?: file.path
        return if (file.isInLocalFileSystem) file.path else file.url
    }

    /** The scope searched for event dispatches: the usual one ([ProjectSearch.scope]) plus the configured paths. */
    fun scope(project: Project): GlobalSearchScope {
        val base = ProjectSearch.scope(project)
        val roots = roots(project)
        if (roots.isEmpty()) return base
        val (folders, files) = roots.partition { it.isDirectory }
        var scope = base
        if (folders.isNotEmpty()) scope = scope.union(GlobalSearchScopesCore.directoriesScope(project, true, *folders.toTypedArray()))
        if (files.isNotEmpty()) scope = scope.union(GlobalSearchScope.filesScope(project, files))
        return scope
    }

    /** Configured paths outside the project folder: they are indexed only when added as library roots. */
    fun rootsOutsideProject(project: Project): List<VirtualFile> {
        val base = project.guessProjectDir()
        return roots(project).filter { base == null || !VfsUtilCore.isAncestor(base, it, false) }
    }
}

/**
 * Adds the configured event sources outside the project as a library, so they are indexed and searched.
 * They appear under *External Libraries* as "Lit event sources".
 */
class EventSourcesLibraryProvider : AdditionalLibraryRootsProvider() {

    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val roots = EventSources.rootsOutsideProject(project)
        if (roots.isEmpty()) return emptyList()
        // Library roots are folders: a single file contributes its folder, and the scope keeps only that file.
        val folders = roots.map { if (it.isDirectory) it else it.parent }.distinct()
        return listOf(SyntheticLibrary.newImmutableLibrary(LIBRARY_ID, folders, emptyList(), emptySet(), null))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> = EventSources.rootsOutsideProject(project)

    companion object {
        const val LIBRARY_ID = "io.github.litscoped.eventSources"
    }
}
