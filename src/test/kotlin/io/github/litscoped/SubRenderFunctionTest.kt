package io.github.litscoped

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Templates rendered by functions outside the component class ("sub render functions"). */
class SubRenderFunctionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "child-el.js",
            """
            export class ChildEl extends LitElement {
              select() { this.dispatchEvent(new CustomEvent('item-selected')); }
            }
            export class OtherEl extends LitElement {
              ping() { this.dispatchEvent(new CustomEvent('item-selected')); }
            }
            """.trimIndent(),
        )
        // Sub render functions in their own module, used by two components.
        myFixture.addFileToProject(
            "renderers.js",
            """
            export function renderBadge() {
              return html`<child-el @item-selected=${"$"}{() => {}}></child-el>`;
            }
            export const renderRow = (item) => html`<div>${"$"}{renderBadge()}</div>`;
            export function unused() {
              return html`<never-checked></never-checked>`;
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "a-el.js",
            """
            import { ChildEl } from './child-el.js';
            import { renderBadge } from './renderers.js';
            export class AEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`${"$"}{renderBadge()}`; }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "b-el.js",
            """
            import { renderRow } from './renderers.js';
            export class BEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {};
              render() { return html`${"$"}{[1, 2].map(renderRow)}`; }
            }
            """.trimIndent(),
        )
    }

    // ------------------------------------------------------------ inspection

    fun testSameFileSubFunctions() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureByText(
            "my-el.js",
            """
            import { ChildEl } from './child-el.js';

            const renderItem = (item) => html`<child-el></child-el><<warning descr="Custom element <missing-el> is not declared in MyEl.scopedElements">missing-el</warning>></missing-el>`;

            function renderList(items) {
              return html`<ul>${"$"}{items.map((i) => html`<li><<warning descr="Custom element <nested-el> is not declared in MyEl.scopedElements">nested-el</warning>></nested-el></li>`)}</ul>${"$"}{items.map(renderItem)}`;
            }

            const footer = html`<child-el></child-el><<warning descr="Custom element <footer-el> is not declared in MyEl.scopedElements">footer-el</warning>></footer-el>`;

            const notUsed = () => html`<anything-el></anything-el>`;

            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`${"$"}{renderList(this.items)}${"$"}{footer}`; }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testSubFunctionInOtherFileCheckedAgainstEveryCaller() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureFromTempProjectFile("renderers.js")
        val warnings = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("scopedElements") }
        // renderBadge is used by AEl (declares child-el) and, through renderRow, by BEl (does not).
        assertEquals(listOf("Custom element <child-el> is not declared in BEl.scopedElements"), warnings)
    }

    fun testQuickFixEditsTheCallingClassInAnotherFile() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureFromTempProjectFile("renderers.js")
        val offset = myFixture.file.text.indexOf("<child-el") + 2
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.launchAction(myFixture.findSingleIntention("Add 'child-el' to BEl.scopedElements"))
        val bText = myFixture.findFileInTempDir("b-el.js").let { psiManagerText(it) }
        assertTrue(bText, bText.contains("'child-el': ChildEl"))
    }

    // ------------------------------------------------------------ navigation

    fun testTagNavigationFromSubFunctionInOtherFile() {
        val targets = targetsAt("renderers.js", "<child-el", 3, ScopedElementGotoDeclarationHandler())
        assertEquals(listOf("ChildEl"), targets.map { (it as JSClass).name })
    }

    fun testNoNavigationFromUnusedFunction() {
        assertEmpty(targetsAt("renderers.js", "<never-checked", 3, ScopedElementGotoDeclarationHandler()))
    }

    fun testEventNavigationFromSubFunctionIsNarrowedToTheTagClass() {
        val targets = targetsAt("renderers.js", "@item-selected", 3, LitEventGotoDeclarationHandler())
        assertEquals(1, targets.size)
        assertEquals("ChildEl", (com.intellij.psi.util.PsiTreeUtil.getParentOfType(targets.single(), JSClass::class.java))?.name)
    }

    // ------------------------------------------------------------- helpers

    private fun psiManagerText(file: com.intellij.openapi.vfs.VirtualFile): String {
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        return com.intellij.psi.PsiManager.getInstance(project).findFile(file)!!.text
    }

    /** Runs [handler] on both the host element and the injected HTML element; both must agree. */
    private fun targetsAt(
        path: String,
        marker: String,
        delta: Int,
        handler: com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler,
    ): List<PsiElement> {
        val file = myFixture.configureFromTempProjectFile(path)
        val manager = InjectedLanguageManager.getInstance(project)
        val hostFile = manager.getTopLevelFile(file)
        val offset = hostFile.text.indexOf(marker) + delta
        val hostTargets = handler.getGotoDeclarationTargets(hostFile.findElementAt(offset), offset, null)?.toList().orEmpty()
        val injected = manager.findInjectedElementAt(hostFile, offset)
        assertNotNull("html`` is expected to be injected", injected)
        val injectedTargets = handler.getGotoDeclarationTargets(injected, offset, null)?.toList().orEmpty()
        assertEquals(hostTargets, injectedTargets)
        return hostTargets
    }
}
