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
class LitSettingsConfigurable(private val project: Project) : BoundConfigurable(LitBundle.message("settings.display.name")) {

    override fun createPanel(): DialogPanel {
        val settings = LitSettings.getInstance(project)
        return panel {
            group(LitBundle.message("settings.group.events")) {
                row(LitBundle.message("settings.event.helpers")) {
                    textField()
                        .columns(COLUMNS_LARGE)
                        .bindText(
                            { settings.eventHelpers.joinToString(", ") },
                            { text -> settings.eventHelpers = text.split(',') },
                        )
                        .comment(LitBundle.message("settings.event.helpers.comment"))
                }
            }
            group(LitBundle.message("settings.group.search")) {
                row {
                    checkBox(LitBundle.message("settings.search.libraries"))
                        .bindSelected({ settings.searchLibraries }, { settings.searchLibraries = it })
                }
            }
        }
    }
}
