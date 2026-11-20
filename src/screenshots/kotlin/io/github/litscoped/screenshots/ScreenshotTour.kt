package io.github.litscoped.screenshots

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Development only (built with `-Pscreenshots`): opens files of the sample project, triggers the plugin's
 * features through the IDE's own actions, paints the IDE windows to PNG files, then exits.
 */
class ScreenshotTour : ProjectActivity {

    private val log = logger<ScreenshotTour>()

    override suspend fun execute(project: Project) {
        val output = System.getProperty("lit.screenshots.dir") ?: return
        val dir = File(output).apply { mkdirs() }
        delay(5000)
        while (DumbService.isDumb(project)) delay(500)
        delay(3000)
        withContext(Dispatchers.EDT) {
            val frame = WindowManager.getInstance().getFrame(project)!!
            frame.setBounds(40, 40, WIDTH, HEIGHT)
        }
        val root = project.basePath!!
        for (shot in shots()) {
            runCatching { take(project, root, dir, shot) }.onFailure { log.warn("Screenshot ${shot.name} failed", it) }
        }
        File(dir, "done").writeText("ok")
        // Exit once this activity is over: exiting from inside it cancels it and leaves projects undisposed.
        ApplicationManager.getApplication().invokeLater({ ApplicationManager.getApplication().exit(true, true, false) }, ModalityState.nonModal())
    }

    private suspend fun take(project: Project, root: String, dir: File, shot: Shot) {
        var original: String? = null
        val editor = withContext(Dispatchers.EDT) {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath("$root/${shot.path}")!!
            val editor = FileEditorManager.getInstance(project).openTextEditor(OpenFileDescriptor(project, file), true)!!
            shot.edit?.let { (marker, replacement) ->
                val document = editor.document
                original = document.text
                val at = document.text.indexOf(marker)
                WriteCommandAction.runWriteCommandAction(project) {
                    document.replaceString(at, at + marker.length, replacement)
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                }
            }
            val text = editor.document.text
            val caret = text.indexOf(shot.caretMarker).also { check(it >= 0) { "marker ${shot.caretMarker}" } } + shot.caretShift
            editor.caretModel.moveToOffset(caret)
            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            editor
        }
        delay(shot.wait) // highlighting and gutter icons
        shot.action?.let { action -> withContext(Dispatchers.EDT) { perform(project, editor, action) } }
        delay(3000)
        withContext(Dispatchers.EDT) {
            NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project).forEach { it.expire() }
        }
        delay(500)
        withContext(Dispatchers.EDT) {
            capture(project, File(dir, "${shot.name}.png"))
            LookupManager.hideActiveLookup(project)
            val frame = WindowManager.getInstance().getFrame(project)!!
            JBPopupFactory.getInstance().getChildPopups(frame.rootPane).forEach { it.cancel() }
            // Undo the shot's edit so the next shots show the file as it is.
            original?.let { text ->
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.setText(text)
                    PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                }
            }
        }
        delay(1000)
    }

    private fun perform(project: Project, editor: Editor, actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId) ?: error("no action $actionId")
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        val context = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, editor)
            .add(CommonDataKeys.PSI_FILE, psiFile)
            .add(CommonDataKeys.VIRTUAL_FILE, psiFile?.virtualFile)
            .add(PlatformCoreDataKeys.CONTEXT_COMPONENT, editor.contentComponent)
            .build()
        ActionUtil.invokeAction(action, context, "EditorPopup", null, null)
    }

    /** Paints the project frame and the windows showing above it (popups) into one image, at 2x. */
    private fun capture(project: Project, file: File) {
        val frame = WindowManager.getInstance().getFrame(project)!!
        val image = BufferedImage(frame.width * SCALE, frame.height * SCALE, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.scale(SCALE.toDouble(), SCALE.toDouble())
        frame.paint(graphics)
        val origin = frame.locationOnScreen
        for (window in Window.getWindows()) {
            if (window == frame || !window.isShowing || window.width == 0) continue
            val at = window.locationOnScreen
            val g = graphics.create(at.x - origin.x, at.y - origin.y, window.width, window.height)
            window.paint(g)
            g.dispose()
        }
        graphics.dispose()
        ImageIO.write(image, "png", file)
    }

    private data class Shot(
        val name: String,
        val path: String,
        val caretMarker: String,
        val caretShift: Int = 1,
        val action: String? = null,
        val edit: Pair<String, String>? = null,
        val wait: Long = 4000,
    )

    private fun shots() = listOf(
        Shot("01-missing-element-quick-fix", "src/my-app.js", "<ui-avatar initials", caretShift = 4, action = "ShowIntentionActions", wait = 10000),
        Shot("02-scoped-element-to-templates", "src/features/base-panel.js", "'ui-button': UiButton", action = "GotoDeclaration"),
        Shot("03-event-listeners", "src/features/user-card.js", "'user-selected', {", action = "GotoDeclaration"),
        Shot(
            "04-property-completion", "src/features/team-panel.js", "<ui-avatar initials=\"AL\" ",
            caretShift = "<ui-avatar initials=\"AL\" ".length, action = "CodeCompletion",
            edit = "<ui-avatar initials=\"AL\">" to "<ui-avatar initials=\"AL\" >",
        ),
        Shot(
            "05-event-completion", "src/features/notifications.js", "<app-toast @",
            caretShift = "<app-toast @".length, action = "CodeCompletion",
            edit = "<ui-badge>2</ui-badge>" to "<ui-badge>2</ui-badge>\n      <app-toast @></app-toast>",
        ),
        Shot(
            "06-tag-completion", "src/features/team-panel.js", "<ui-badge>3</ui-badge>\n      <",
            caretShift = "<ui-badge>3</ui-badge>\n      <".length, action = "CodeCompletion",
            edit = "<ui-badge>3</ui-badge>" to "<ui-badge>3</ui-badge>\n      <",
        ),
        Shot("07-quick-documentation", "src/features/notifications.js", "<ui-badge>2", caretShift = 2, action = "QuickJavaDoc"),
        Shot("08-event-never-fired", "src/features/notifications.js", "@toast-vanished=", caretShift = 3, action = "ShowErrorDescription"),
    )

    private companion object {
        const val WIDTH = 1440
        const val HEIGHT = 900
        const val SCALE = 2
    }
}
