package io.github.litscoped

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.COLUMNS_LARGE
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel

/** Settings | Languages & Frameworks | Lit Scoped Elements */
class LitSettingsConfigurable(private val project: Project) : BoundConfigurable(LitBundle.message("settings.display.name")) {

    override fun createPanel(): DialogPanel {
        val settings = LitSettings.getInstance(project)
        val sources = CollectionListModel<String>()
        val sourceList = JBList(sources).apply { visibleRowCount = 5 }
        val sourcesPanel = ToolbarDecorator.createDecorator(sourceList)
            .setAddAction { chooseSources(sources) }
            .disableUpDownActions()
            .createPanel()

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
                row {
                    label(LitBundle.message("settings.event.sources"))
                }
                row {
                    cell(sourcesPanel)
                        .align(Align.FILL)
                        .comment(LitBundle.message("settings.event.sources.comment"))
                        .onReset { sources.replaceAll(settings.eventSources) }
                        .onIsModified { sources.items != settings.eventSources }
                        .onApply { settings.eventSources = sources.items.toList() }
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

    private fun chooseSources(sources: CollectionListModel<String>) {
        val descriptor = FileChooserDescriptor(true, true, false, false, false, true)
            .withTitle(LitBundle.message("settings.event.sources.choose"))
        FileChooser.chooseFiles(descriptor, project, project.guessProjectDir()) { files ->
            for (file in files) {
                val path = EventSources.pathOf(project, file)
                if (path !in sources.items) sources.add(path)
            }
        }
    }
}
