package io.github.litscoped

import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RenameAndUsagesTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("child.js", "export class ChildEl extends HTMLElement {}")
        myFixture.addFileToProject(
            "renderers.js",
            "export function renderChild() { return html`<child-el></child-el>`; }",
        )
        myFixture.addFileToProject(
            "my-el.js",
            """
            import { ChildEl } from './child.js';
            import { renderChild } from './renderers.js';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`<child-el a="1"></child-el>${"$"}{renderChild()}`; }
            }
            class OtherEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`<child-el b="2"></child-el>`; }
            }
            """.trimIndent(),
        )
    }

    private fun myElKey(): JSProperty {
        val file = myFixture.configureFromTempProjectFile("my-el.js")
        val offset = file.text.indexOf("'child-el'") + 2
        return PsiTreeUtil.getParentOfType(file.findElementAt(offset), JSProperty::class.java)!!
    }

    fun testFindUsagesOfKey() {
        val usages = ReferencesSearch.search(myElKey()).findAll()
            .map { "${it.element.containingFile.name}:${it.rangeInElement.substring(it.element.text)}" }
            .sorted()
        // Opening and closing tags of MyEl's template and of the sub function it calls; not OtherEl's.
        assertEquals(
            listOf("my-el.js:child-el", "my-el.js:child-el", "renderers.js:child-el", "renderers.js:child-el"),
            usages,
        )
    }

    fun testRenameKeyRenamesTags() {
        myFixture.renameElement(myElKey(), "kid-el")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val myEl = myFixture.findFileInTempDir("my-el.js").let { psiManager.findFile(it)!!.text }
        val renderers = myFixture.findFileInTempDir("renderers.js").let { psiManager.findFile(it)!!.text }
        assertTrue(myEl, myEl.contains("static scopedElements = { 'kid-el': ChildEl };\n  render() { return html`<kid-el a=\"1\"></kid-el>"))
        // OtherEl keeps its own entry and tags.
        assertTrue(myEl, myEl.contains("static scopedElements = { 'child-el': ChildEl };\n  render() { return html`<child-el b=\"2\"></child-el>"))
        assertEquals("export function renderChild() { return html`<kid-el></kid-el>`; }", renderers)
    }
}
