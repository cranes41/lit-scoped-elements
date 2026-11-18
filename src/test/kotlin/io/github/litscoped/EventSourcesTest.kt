package io.github.litscoped

import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The ways of firing an event besides `new CustomEvent('x')`, and the library search option. */
class EventSourcesTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "events.js",
            """
            export class SelectEvent extends Event {
              constructor() { super('select-item', { bubbles: true }); }
            }
            export class NamedEvent extends CustomEvent {
              static NAME = 'named-event';
              constructor(detail) { super(NamedEvent.NAME, { detail }); }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "emitter.js",
            """
            import { SelectEvent, NamedEvent } from './events.js';
            export class Emitter extends LitElement {
              a() { this.fire('helper-event', { x: 1 }); }
              b() { this.dispatchEvent(new SelectEvent()); }
              c() { this.dispatchEvent(new NamedEvent(1)); }
              d() { this.dispatchEvent(new CustomEvent(`tpl-event`)); }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "listener.js",
            """
            class Listener extends LitElement {
              render() {
                return html`<div @helper-event=${"$"}{h} @select-item=${"$"}{h} @named-event=${"$"}{h} @tpl-event=${"$"}{h} @lib-event=${"$"}{h}></div>`;
              }
            }
            """.trimIndent(),
        )
    }

    override fun tearDown() {
        try {
            LitSettings.getInstance(project).loadState(LitSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testHelperCall() {
        assertEquals(listOf("Emitter:'helper-event'"), dispatchesOf("@helper-event"))
        // Without the helper configured, nothing fires it (and the cache follows the settings).
        LitSettings.getInstance(project).eventHelpers = listOf("emit")
        assertEmpty(dispatchesOf("@helper-event"))
    }

    fun testEventSubclassIsFiredWhereItIsInstantiated() {
        assertEquals(listOf("Emitter:new SelectEvent()"), dispatchesOf("@select-item"))
        assertEquals(listOf("Emitter:new NamedEvent(1)"), dispatchesOf("@named-event"))
    }

    fun testTemplateLiteralName() {
        assertEquals(listOf("Emitter:`tpl-event`"), dispatchesOf("@tpl-event"))
    }

    fun testClickOnHelperAndSuperArgumentsGoesToListeners() {
        for (marker in listOf("'helper-event'", "`tpl-event`")) {
            val file = myFixture.configureFromTempProjectFile("emitter.js")
            val offset = file.text.indexOf(marker) + 2
            val targets = LitEventGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
            assertEquals(marker, listOf("listener.js"), targets.orEmpty().map { it.containingFile.name })
        }
        val file = myFixture.configureFromTempProjectFile("events.js")
        val offset = file.text.indexOf("'select-item'") + 2
        val targets = LitEventGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        assertEquals(listOf("listener.js"), targets.orEmpty().map { it.containingFile.name })
    }

    fun testLibrariesAreSearchedOnlyWhenEnabled() {
        val libraryRoot = WriteAction.computeAndWait<VirtualFile, Exception> {
            LightPlatformTestCase.getSourceRoot().parent.createChildDirectory(this, "fake-node-modules")
        }
        VfsTestUtil.createFile(
            libraryRoot, "lib-el.js",
            "export class LibEl extends HTMLElement { f() { this.dispatchEvent(new CustomEvent('lib-event')); } }",
        )
        val library = PsiTestUtil.addProjectLibrary(module, "fake-lib", listOf(libraryRoot), emptyList())
        try {
            assertEmpty(dispatchesOf("@lib-event"))
            LitSettings.getInstance(project).searchLibraries = true
            assertEquals(listOf("LibEl:'lib-event'"), dispatchesOf("@lib-event"))
        } finally {
            PsiTestUtil.removeLibrary(module, library)
            WriteAction.runAndWait<Exception> {
                com.intellij.openapi.roots.libraries.LibraryTablesRegistrar.getInstance().getLibraryTable(project).removeLibrary(library)
                libraryRoot.delete(this)
            }
        }
    }

    /** Dispatch targets of the `@event` binding at [marker] in listener.js, as "EnclosingClass:text". */
    private fun dispatchesOf(marker: String): List<String> {
        val file = myFixture.configureFromTempProjectFile("listener.js")
        val offset = file.text.indexOf(marker) + 2
        val targets = LitEventGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        return targets.orEmpty().map(::describe).sorted()
    }

    private fun describe(element: PsiElement): String =
        "${PsiTreeUtil.getParentOfType(element, JSClass::class.java, false)?.name}:${element.text}"
}
