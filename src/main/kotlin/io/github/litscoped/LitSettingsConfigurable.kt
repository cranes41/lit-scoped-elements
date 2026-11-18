package io.github.litscoped

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.panel

/** Settings | Languages & Frameworks | Lit Scoped Elements */
class LitSettingsConfigurable(private val project: Project) : BoundConfigurable("Lit Scoped Elements") {

    override fun createPanel(): DialogPanel {
        val settings = LitSettings.getInstance(project)
        return panel {
            group("Events") {
                row("Event helper functions:") {
                    textField()
                        .columns(COLUMNS_LARGE)
                        .bindText(
                            { settings.eventHelpers.joinToString(", ") },
                            { text -> settings.eventHelpers = text.split(',') },
                        )
                        .comment("Comma-separated. Calls such as <code>this.fire('my-event')</code> count as firing the event.")
                }
            }
            group("Search") {
                row {
                    checkBox("Search libraries (node_modules) for event dispatches and global element definitions")
                        .bindSelected({ settings.searchLibraries }, { settings.searchLibraries = it })
                }
            }
        }
    }
}
