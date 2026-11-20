package io.github.litscoped

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.vfs.VirtualFile

/** Project settings of the plugin, stored in `.idea/litScopedElements.xml`. */
@Service(Service.Level.PROJECT)
@State(name = "LitScopedElements", storages = [Storage("litScopedElements.xml")])
class LitSettings(private val project: Project) : PersistentStateComponent<LitSettings.State> {

    class State {
        /** Functions whose first argument is the name of an event they fire, e.g. `this.fire('x')`. */
        var eventHelpers: MutableList<String> = DEFAULT_EVENT_HELPERS.toMutableList()

        /** Also search libraries (node_modules) for event dispatches and global element definitions. */
        var searchLibraries: Boolean = false

        /** Files and folders also searched for event dispatches: relative to the project, absolute, or URLs. */
        var eventSources: MutableList<String> = mutableListOf()
    }

    private var state = State()

    /** Incremented on every change, so caches depending on the settings are dropped. */
    val tracker = SimpleModificationTracker()

    override fun getState(): State = state

    override fun loadState(state: State) {
        val oldOutside = if (project.isDefault) emptyList() else EventSources.rootsOutsideProject(project)
        this.state = state
        tracker.incModificationCount()
        if (oldOutside.isNotEmpty() || state.eventSources.isNotEmpty()) outsideRootsChanged(oldOutside)
    }

    var eventHelpers: List<String>
        get() = state.eventHelpers
        set(value) {
            state.eventHelpers = value.map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()
            tracker.incModificationCount()
        }

    var searchLibraries: Boolean
        get() = state.searchLibraries
        set(value) {
            state.searchLibraries = value
            tracker.incModificationCount()
        }

    var eventSources: List<String>
        get() = state.eventSources
        set(value) {
            val oldOutside = EventSources.rootsOutsideProject(project)
            state.eventSources = value.map(String::trim).filter(String::isNotEmpty).distinct().toMutableList()
            tracker.incModificationCount()
            outsideRootsChanged(oldOutside)
        }

    /** Called when the project becomes trusted: event sources outside it can now be used. */
    fun projectTrusted() {
        tracker.incModificationCount()
        outsideRootsChanged(emptyList())
    }

    /** Re-indexes when the event sources outside the project, added as a library, change. */
    private fun outsideRootsChanged(oldOutside: List<VirtualFile>) {
        if (project.isDefault || project.isDisposed) return
        val newOutside = EventSources.rootsOutsideProject(project)
        if (oldOutside == newOutside) return
        val fire = {
            WriteAction.run<RuntimeException> {
                AdditionalLibraryRootsListener.fireAdditionalLibraryChanged(
                    project, LitBundle.message("settings.event.sources.library"), oldOutside, newOutside,
                    EventSourcesLibraryProvider.LIBRARY_ID,
                )
            }
        }
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread) fire() else application.invokeLater(fire, project.disposed)
    }

    companion object {
        val DEFAULT_EVENT_HELPERS = listOf("fire", "emit")

        fun getInstance(project: Project): LitSettings = project.service()
    }
}
