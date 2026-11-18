package io.github.litscoped

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LitEventsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "events.js",
            """
            export const ITEM_REMOVED = 'item-removed';
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "child-el.js",
            """
            import { ITEM_REMOVED } from './events.js';
            export class ChildEl extends LitElement {
              select() { this.dispatchEvent(new CustomEvent('item-selected', { bubbles: true, composed: true })); }
              remove() { this.dispatchEvent(new Event(ITEM_REMOVED)); }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "other-el.js",
            """
            export class OtherEl extends LitElement {
              ping() { this.dispatchEvent(new CustomEvent('item-selected')); }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "listener.js",
            """
            document.addEventListener('item-selected', () => {});
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "parent-el.js",
            """
            import { ChildEl } from './child-el.js';
            class ParentEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() {
                return html`
                  <child-el @item-selected=${"$"}{this._onSelect} @item-removed=${"$"}{this._onRemove}></child-el>
                  <div @item-selected=${"$"}{this._onSelect}></div>
                  <div @click=${"$"}{this._onClick}></div>
                `;
              }
            }
            """.trimIndent(),
        )
    }

    // ---------------------------------------------------- @event -> dispatches

    fun testBindingOnScopedElementGoesToItsDispatch() {
        val targets = targetsAt("parent-el.js", "@item-selected", 3)
        assertEquals(listOf("child-el.js:'item-selected'"), targets.map(::describe))
    }

    fun testBindingOnUnknownElementListsAllDispatches() {
        val targets = targetsAt("parent-el.js", "<div @item-selected", 8)
        assertEquals(
            listOf("child-el.js:'item-selected'", "other-el.js:'item-selected'"),
            targets.map(::describe).sorted(),
        )
    }

    fun testEventNameFromConstant() {
        val targets = targetsAt("parent-el.js", "@item-removed", 3)
        assertEquals(listOf("child-el.js:ITEM_REMOVED"), targets.map(::describe))
    }

    fun testNativeEventWithoutDispatchIsNotHandled() {
        assertEmpty(targetsAt("parent-el.js", "@click", 3))
    }

    // ---------------------------------------------------- dispatch -> listeners

    fun testDispatchGoesToListeners() {
        val targets = targetsAt("child-el.js", "'item-selected'", 3)
        val described = targets.map(::describe).sorted()
        assertEquals(3, described.size)
        assertEquals(2, described.count { it.startsWith("parent-el.js:@item-selected") })
        assertTrue(described.toString(), described.contains("listener.js:'item-selected'"))
    }

    fun testAddEventListenerGoesToDispatches() {
        val targets = targetsAt("listener.js", "'item-selected'", 3)
        assertEquals(
            listOf("child-el.js:'item-selected'", "other-el.js:'item-selected'"),
            targets.map(::describe).sorted(),
        )
    }

    fun testGotoDeclarationActionOnBinding() {
        myFixture.configureFromTempProjectFile("parent-el.js")
        val text = myFixture.file.text
        myFixture.editor.caretModel.moveToOffset(text.indexOf("@item-selected") + 3)
        myFixture.performEditorAction("GotoDeclaration")
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor!!
        val file = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        assertEquals("child-el.js", file.name)
    }

    // ------------------------------------------------------------- helpers

    /** Runs the handler on both the host element and the injected HTML element at `marker + delta`. */
    private fun targetsAt(path: String, marker: String, delta: Int): List<PsiElement> {
        val file: PsiFile = myFixture.configureFromTempProjectFile(path)
        val hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file)
        val offset = hostFile.text.indexOf(marker).also { assertTrue("marker $marker", it >= 0) } + delta
        val handler = LitEventGotoDeclarationHandler()
        val hostTargets = handler.getGotoDeclarationTargets(hostFile.findElementAt(offset), offset, null)?.toList().orEmpty()
        val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, offset)
        if (path == "parent-el.js") assertNotNull("html`` is expected to be injected", injected)
        if (injected != null) {
            val injectedTargets = handler.getGotoDeclarationTargets(injected, offset, null)?.toList().orEmpty()
            assertEquals("host and injected results differ", hostTargets.map(::describe).sorted(), injectedTargets.map(::describe).sorted())
        }
        return hostTargets
    }

    private fun describe(element: PsiElement): String {
        val hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(element)
        return "${hostFile.name}:${element.text.substringBefore('=')}"
    }
}
