package io.github.litscoped

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker

/** Project settings of the plugin, stored in `.idea/litScopedElements.xml`. */
@Service(Service.Level.PROJECT)
@State(name = "LitScopedElements", storages = [Storage("litScopedElements.xml")])
class LitSettings : PersistentStateComponent<LitSettings.State> {

    class State {
        /** Functions whose first argument is the name of an event they fire, e.g. `this.fire('x')`. */
        var eventHelpers: MutableList<String> = DEFAULT_EVENT_HELPERS.toMutableList()

        /** Also search libraries (node_modules) for event dispatches and global element definitions. */
        var searchLibraries: Boolean = false
    }

    private var state = State()

    /** Incremented on every change, so caches depending on the settings are dropped. */
    val tracker = SimpleModificationTracker()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        tracker.incModificationCount()
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

    companion object {
        val DEFAULT_EVENT_HELPERS = listOf("fire", "emit")

        fun getInstance(project: Project): LitSettings = project.service()
    }
}
