package io.github.litscoped

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ScopedElementsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "child-el.js",
            """
            export class ChildEl extends HTMLElement {}
            export class OtherEl extends HTMLElement {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "base-el.js",
            """
            import { ChildEl } from './child-el.js';
            export class BaseEl extends HTMLElement {
              static scopedElements = { 'base-child': ChildEl };
            }
            """.trimIndent(),
        )
    }

    // ------------------------------------------------------------ inspection

    fun testReportsUndeclaredElements() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureByText(
            "my-el.js",
            """
            import { ChildEl } from './child-el.js';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() {
                return html`
                  <child-el></child-el>
                  <!-- <commented-el></commented-el> -->
                  <div title=${"$"}{'<not-a-tag>'}></div>
                  ${"$"}{this.items.map(() => html`<<warning descr="Custom element <nested-el> is not declared in MyEl.scopedElements">nested-el</warning>></nested-el>`)}
                  <<warning descr="Custom element <missing-el> is not declared in MyEl.scopedElements">missing-el</warning> foo="bar"></missing-el>
                `;
              }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testGetterSpreadAndInheritance() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureByText(
            "my-el.js",
            """
            import { OtherEl } from './child-el.js';
            import { BaseEl } from './base-el.js';
            const shared = { 'shared-el': OtherEl };
            class MyEl extends BaseEl {
              static get scopedElements() {
                return { ...super.scopedElements, ...shared, 'other-el': OtherEl };
              }
              render() {
                return html`<base-child></base-child><shared-el></shared-el><other-el></other-el><<warning descr="Custom element <x-y> is not declared in MyEl.scopedElements">x-y</warning>></x-y>`;
              }
            }
            class InheritsOnly extends BaseEl {
              render() { return html`<base-child></base-child><<warning descr="Custom element <x-z> is not declared in InheritsOnly.scopedElements">x-z</warning>></x-z>`; }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testIgnoresClassesWithoutScopedElements() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureByText(
            "plain.js",
            """
            class Plain extends LitElement {
              render() { return html`<anything-el></anything-el>`; }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testIgnoredTagsOption() {
        val inspection = MissingScopedElementInspection().apply { ignoredTags = mutableListOf("sl-.*") }
        myFixture.enableInspections(inspection)
        myFixture.configureByText(
            "my-el.js",
            """
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {};
              render() { return html`<sl-button></sl-button>`; }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testQuickFixAddsEntry() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureByText(
            "my-el.js",
            """
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {
                'child-el': ChildEl,
              };
              render() { return html`<my-<caret>child-el></my-child-el>`; }
            }
            """.trimIndent(),
        )
        myFixture.launchAction(myFixture.findSingleIntention("Add 'my-child-el' to scopedElements"))
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        val text = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file).text
        assertTrue(text, text.contains("'my-child-el': MyChildEl"))
        assertTrue(text, text.contains("'child-el': ChildEl,"))
    }

    // ------------------------------------------------------------ navigation

    private val navigationSource = """
        import { ChildEl } from './child-el.js';
        class MyEl extends ScopedElementsMixin(LitElement) {
          static scopedElements = { 'child-el': ChildEl };
          render() { return html`<child-<caret>el></child-el>`; }
        }
    """.trimIndent()

    fun testNavigatesFromTemplateLiteral() {
        myFixture.configureByText("my-el.js", navigationSource)
        val (hostFile, hostOffset) = hostCaret()
        // Element of the JS template literal itself (the path used when HTML is not injected).
        val element = hostFile.findElementAt(hostOffset)!!
        assertNull(InjectedLanguageManager.getInstance(project).getInjectionHost(element))
        assertNavigatesToChildEl(ScopedElementGotoDeclarationHandler().getGotoDeclarationTargets(element, hostOffset, null))
    }

    fun testNavigatesFromInjectedHtml() {
        myFixture.configureByText("my-el.js", navigationSource)
        val (hostFile, hostOffset) = hostCaret()
        val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, hostOffset)
        assertNotNull("html`` template is expected to be injected as HTML", injected)
        assertNavigatesToChildEl(ScopedElementGotoDeclarationHandler().getGotoDeclarationTargets(injected, hostOffset, null))
    }

    fun testNavigatesFromClosingTag() {
        myFixture.configureByText(
            "my-el.js",
            navigationSource.replace("<child-<caret>el></child-el>", "<child-el></child-<caret>el>"),
        )
        val (hostFile, hostOffset) = hostCaret()
        val handler = ScopedElementGotoDeclarationHandler()
        assertNavigatesToChildEl(handler.getGotoDeclarationTargets(hostFile.findElementAt(hostOffset), hostOffset, null))
        val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, hostOffset)
        assertNavigatesToChildEl(handler.getGotoDeclarationTargets(injected, hostOffset, null))
    }

    fun testGotoDeclarationActionUsesHandler() {
        myFixture.configureByText("my-el.js", navigationSource)
        myFixture.performEditorAction("GotoDeclaration")
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor!!
        val file = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        assertEquals("child-el.js", file.name)
        assertTrue(file.text.substring(editor.caretModel.offset).startsWith("ChildEl") ||
            file.text.substring(editor.caretModel.offset).startsWith("class ChildEl"))
    }

    fun testNoNavigationForUnknownTag() {
        myFixture.configureByText("my-el.js", navigationSource.replace("child-<caret>el>", "unknown-<caret>el>"))
        val (hostFile, hostOffset) = hostCaret()
        assertNull(ScopedElementGotoDeclarationHandler().getGotoDeclarationTargets(hostFile.findElementAt(hostOffset), hostOffset, null))
    }

    /** The fixture switches to the injected editor when the caret is inside html``; work on the host file instead. */
    private fun hostCaret(): Pair<com.intellij.psi.PsiFile, Int> {
        val editor = com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)
        val file = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        return file to editor.caretModel.offset
    }

    private fun assertNavigatesToChildEl(targets: Array<*>?) {
        assertNotNull(targets)
        val target = targets!!.single()
        assertTrue("Expected class, got $target", target is JSClass)
        assertEquals("ChildEl", (target as JSClass).name)
        assertEquals("child-el.js", target.containingFile.name)
    }
}
