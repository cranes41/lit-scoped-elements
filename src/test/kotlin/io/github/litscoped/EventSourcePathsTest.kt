package io.github.litscoped

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Files and folders added in the settings as places where events are fired. */
class EventSourcePathsTest : BasePlatformTestCase() {

    private val created = mutableListOf<VirtualFile>()

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "listener.js",
            "class Listener extends LitElement { render() { return html`<div @lib-event=${'$'}{h} @outside-event=${'$'}{h} @file-event=${'$'}{h}></div>`; } }",
        )
    }

    override fun tearDown() {
        try {
            LitSettings.getInstance(project).loadState(LitSettings.State())
            WriteAction.runAndWait<Exception> { created.forEach { it.delete(this) } }
        } finally {
            super.tearDown()
        }
    }

    fun testFolderOfALibrary() {
        val library = folder("fake-node-modules", "lib-el.js", "class LibEl extends HTMLElement { f() { this.dispatchEvent(new CustomEvent('lib-event')); } }")
        val registered = PsiTestUtil.addProjectLibrary(module, "fake-lib", listOf(library), emptyList())
        try {
            assertEmpty(dispatchesOf("@lib-event")) // a library, and "Search libraries" is off
            LitSettings.getInstance(project).eventSources = listOf(library.url)
            assertEquals(listOf("LibEl:'lib-event'"), dispatchesOf("@lib-event"))
        } finally {
            PsiTestUtil.removeLibrary(module, registered)
            WriteAction.runAndWait<Exception> {
                com.intellij.openapi.roots.libraries.LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeLibrary(registered)
            }
        }
    }

    fun testFolderOutsideTheProject() {
        val outside = folder("outside-sources", "out-el.js", "class OutEl extends HTMLElement { f() { this.dispatchEvent(new CustomEvent('outside-event')); } }")
        assertEmpty(dispatchesOf("@outside-event")) // not indexed at all
        LitSettings.getInstance(project).eventSources = listOf(outside.url)
        assertEquals(listOf("OutEl:'outside-event'"), dispatchesOf("@outside-event"))
        // Removing the folder from the settings removes its events.
        LitSettings.getInstance(project).eventSources = emptyList()
        assertEmpty(dispatchesOf("@outside-event"))
    }

    fun testSingleFile() {
        val dir = folder("single-file-sources", "file-el.js", "class FileEl extends HTMLElement { f() { this.dispatchEvent(new CustomEvent('file-event')); } }")
        VfsTestUtil.createFile(dir, "other-el.js", "class OtherEl extends HTMLElement { f() { this.dispatchEvent(new CustomEvent('file-event')); } }")
        LitSettings.getInstance(project).eventSources = listOf(dir.findChild("file-el.js")!!.url)
        assertEquals(listOf("FileEl:'file-event'"), dispatchesOf("@file-event"))
    }

    fun testPathsAreResolvedRelativeToTheProject() {
        val inProject = myFixture.addFileToProject("events/emitter.js", "class E extends HTMLElement {}").virtualFile.parent
        val base = project.guessProjectDir()!!
        val relative = com.intellij.openapi.vfs.VfsUtilCore.getRelativePath(inProject, base)!!
        assertEquals(inProject, EventSources.resolve(project, relative))
        assertEquals(inProject, EventSources.resolve(project, inProject.url))
        assertNull(EventSources.resolve(project, "does/not/exist"))
    }

    fun testNetworkPathsAreRejectedBeforeAnyLookup() {
        for (path in listOf("\\\\server\\share", "//server/share", "file://server/share", "file:////server/share", "FILE://server/x")) {
            assertTrue(path, EventSources.isNetworkPath(path))
            assertNull(path, EventSources.resolve(project, path, trusted = true))
        }
        for (path in listOf("/Users/me/lib", "file:///Users/me/lib", "node_modules/pkg", "C:\\lib")) {
            assertFalse(path, EventSources.isNetworkPath(path))
        }
    }

    fun testUntrustedProjectOnlyResolvesPathsInsideIt() {
        val inProject = myFixture.addFileToProject("events/emitter.js", "class E extends HTMLElement {}").virtualFile.parent
        val outside = folder("untrusted-outside", "x.js", "class X extends HTMLElement {}")
        val relative = VfsUtilCore.getRelativePath(inProject, project.guessProjectDir()!!)!!
        assertEquals(inProject, EventSources.resolve(project, relative, trusted = false))
        assertNull(EventSources.resolve(project, outside.url, trusted = false))
        assertNull(EventSources.resolve(project, "/tmp", trusted = false))
        assertNull(EventSources.resolve(project, "../untrusted-outside", trusted = false))
        assertNull(EventSources.resolve(project, "events\\..\\..\\untrusted-outside", trusted = false))
        assertEquals(outside, EventSources.resolve(project, "../untrusted-outside", trusted = true))
        assertEquals(outside, EventSources.resolve(project, outside.url, trusted = true))
    }

    fun testUntrustedOrBroadPathsAreRejected() {
        // Only local files: other URL schemes are ignored.
        assertNull(EventSources.resolve(project, "http://example.com/events.js"))
        assertNull(EventSources.resolve(project, "jar:///some.jar!/events.js"))
        // Never a filesystem root or the home folder (or a folder containing it).
        val home = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(System.getProperty("user.home"))!!
        assertFalse(EventSources.isAcceptable(home))
        assertFalse(EventSources.isAcceptable(home.parent))
        var root = home
        while (root.parent != null) root = root.parent
        assertFalse(EventSources.isAcceptable(root))
        LitSettings.getInstance(project).eventSources = listOf(home.path, root.path)
        assertEmpty(EventSources.roots(project))
    }

    private fun folder(name: String, fileName: String, content: String): VirtualFile {
        val dir = WriteAction.computeAndWait<VirtualFile, Exception> {
            LightPlatformTestCase.getSourceRoot().parent.let { it.findChild(name) ?: it.createChildDirectory(this, name) }
        }
        created += dir
        VfsTestUtil.createFile(dir, fileName, content)
        return dir
    }

    private fun dispatchesOf(marker: String): List<String> {
        val file = myFixture.configureFromTempProjectFile("listener.js")
        val offset = file.text.indexOf(marker) + 2
        val targets = LitEventGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        return targets.orEmpty().map {
            "${com.intellij.psi.util.PsiTreeUtil.getParentOfType(it, com.intellij.lang.javascript.psi.ecmal4.JSClass::class.java, false)?.name}:${it.text}"
        }.sorted()
    }
}
