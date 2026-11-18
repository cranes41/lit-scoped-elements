package io.github.litscoped

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Cmd+Click on a `scopedElements` key -> usages of the tag in the templates the entry applies to. */
class ScopedElementUsagesTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("child-el.js", "export class ChildEl extends HTMLElement {}")
        myFixture.addFileToProject(
            "shared.js",
            """
            import { ChildEl } from './child-el.js';
            export const shared = { 'child-el': ChildEl };
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "renderers.js",
            """
            export function renderChild() { return html`<child-el id="sub"></child-el>`; }
            export function unused() { return html`<child-el id="unused"></child-el>`; }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "my-el.js",
            """
            import { ChildEl } from './child-el.js';
            import { renderChild } from './renderers.js';
            export class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`<child-el id="render"></child-el>${"$"}{this._more()}${"$"}{renderChild()}`; }
              _more() { return html`<child-el id="helper"></child-el>`; }
            }
            // Inherits MyEl's entry.
            export class SubEl extends MyEl {
              render() { return html`<child-el id="inherited"></child-el>`; }
            }
            // Spreads MyEl's entry.
            export class SpreadEl extends MyEl {
              static scopedElements = { ...super.scopedElements };
              render() { return html`<child-el id="spread-super"></child-el>`; }
            }
            // Own declaration of the same tag: not a usage of MyEl's entry.
            export class OtherEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`<child-el id="other"></child-el>`; }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "shared-user.js",
            """
            import { shared } from './shared.js';
            export class SharedUser extends ScopedElementsMixin(LitElement) {
              static scopedElements = { ...shared };
              render() { return html`<child-el id="shared"></child-el>`; }
            }
            """.trimIndent(),
        )
    }

    fun testUsagesOfClassEntry() {
        assertEquals(
            listOf("helper", "inherited", "render", "spread-super", "sub"),
            usageIds("my-el.js", "'child-el': ChildEl };\n  render() { return html`<child-el id=\"render\""),
        )
    }

    fun testOtherDeclarationOfSameTagIsIndependent() {
        assertEquals(listOf("other"), usageIds("my-el.js", "'child-el': ChildEl };\n  render() { return html`<child-el id=\"other\""))
    }

    fun testUsagesOfSharedEntry() {
        assertEquals(listOf("shared"), usageIds("shared.js", "'child-el'"))
    }

    fun testOnlyScopedElementsObjectsAreConsidered() {
        myFixture.addFileToProject(
            "nested-shared.js",
            """
            import { shared } from './shared.js';
            export const moreShared = { ...shared, 'x-y': X };
            export const notSpread = { 'x-z': X };
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "nested-user.js",
            """
            import { moreShared } from './nested-shared.js';
            class NestedUser extends ScopedElementsMixin(LitElement) {
              static get scopedElements() { return { ...moreShared }; }
              send() { return fetch('/', { headers: { 'content-type': 'json' } }); }
            }
            const labels = { 'login-title': 'Login' };
            """.trimIndent(),
        )
        fun isScoped(path: String, marker: String): Boolean {
            val file = myFixture.configureFromTempProjectFile(path)
            val element = file.findElementAt(file.text.indexOf(marker) + 1)!!
            val literal = com.intellij.psi.util.PsiTreeUtil.getParentOfType(
                element, com.intellij.lang.javascript.psi.JSObjectLiteralExpression::class.java,
            )!!
            return ScopedElements.isScopedElementsObject(literal)
        }
        assertTrue(isScoped("my-el.js", "'child-el': ChildEl };\n  render() { return html`<child-el id=\"render\""))
        assertTrue("spread const", isScoped("shared.js", "'child-el'"))
        assertTrue("spread through another spread object", isScoped("nested-shared.js", "'x-y'"))
        assertFalse("const never spread", isScoped("nested-shared.js", "'x-z'"))
        assertFalse("object inside a class method", isScoped("nested-user.js", "'content-type'"))
        assertFalse("unrelated const", isScoped("nested-user.js", "'login-title'"))

        // The handler gives up on such keys.
        val file = myFixture.configureFromTempProjectFile("nested-user.js")
        val offset = file.text.indexOf("'login-title'") + 2
        assertNull(ScopedElementUsagesGotoHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null))
    }

    fun testOnlyKeyIsHandled() {
        myFixture.configureFromTempProjectFile("my-el.js")
        val text = myFixture.file.text
        val offset = text.indexOf("ChildEl };") + 2 // on the value, not the key
        assertNull(ScopedElementUsagesGotoHandler().getGotoDeclarationTargets(myFixture.file.findElementAt(offset), offset, null))
    }

    fun testGotoDeclarationActionOpensTheTemplate() {
        myFixture.configureFromTempProjectFile("shared.js")
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("'child-el'") + 3)
        myFixture.performEditorAction("GotoDeclaration")
        val editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedTextEditor!!
        val file = com.intellij.psi.PsiDocumentManager.getInstance(project).getPsiFile(editor.document)!!
        assertEquals("shared-user.js", file.name)
        val after = file.text.substring(editor.caretModel.offset)
        assertTrue(after.take(40), after.startsWith("<child-el id=\"shared\""))
    }

    /** Clicks the key whose declaration starts at [marker] and returns the `id` of each usage found. */
    private fun usageIds(path: String, marker: String): List<String> {
        myFixture.configureFromTempProjectFile(path)
        val text = myFixture.file.text
        val start = text.indexOf(marker).also { assertTrue("marker not found: $marker", it >= 0) }
        val offset = start + 3
        val targets = ScopedElementUsagesGotoHandler()
            .getGotoDeclarationTargets(myFixture.file.findElementAt(offset), offset, null)
            .orEmpty()
        return targets.map(::idOf).sorted()
    }

    private fun idOf(element: PsiElement): String {
        val manager = InjectedLanguageManager.getInstance(project)
        val host = manager.getTopLevelFile(element)
        val offset = if (manager.isInjectedFragment(element.containingFile)) manager.injectedToHost(element, element.textRange.startOffset)
        else element.textRange.startOffset
        return Regex("""id="([^"]+)"""").find(host.text, offset)!!.groupValues[1]
    }
}
