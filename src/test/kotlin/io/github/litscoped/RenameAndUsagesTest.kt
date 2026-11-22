package io.github.litscoped

import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.rename.RenameInputValidatorRegistry
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

    /** A base class template is rendered by a subclass registering the same tag with its own entry. */
    private fun addSharedTemplate(): Pair<JSProperty, JSProperty> {
        val file = myFixture.addFileToProject(
            "shared.js",
            """
            class A extends HTMLElement {}
            class B extends HTMLElement {}
            class Base extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'x-a': A };
              render() { return html`<x-a></x-a>`; }
            }
            class Sub extends Base { static scopedElements = { 'x-a': B }; }
            """.trimIndent(),
        )
        fun key(occurrence: String): JSProperty {
            val offset = file.text.indexOf(occurrence) + occurrence.indexOf("'x-a'") + 2
            return PsiTreeUtil.getParentOfType(file.findElementAt(offset), JSProperty::class.java)!!
        }
        return key("{ 'x-a': A }") to key("{ 'x-a': B }")
    }

    fun testRenameKeyRenamesTheKeysSharingATemplate() {
        val (_, subKey) = addSharedTemplate()
        myFixture.renameElement(subKey, "x-b")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val text = myFixture.findFileInTempDir("shared.js").let { psiManager.findFile(it)!!.text }
        assertTrue(text, text.contains("static scopedElements = { 'x-b': A };\n  render() { return html`<x-b></x-b>`; }"))
        assertTrue(text, text.contains("class Sub extends Base { static scopedElements = { 'x-b': B }; }"))
    }

    fun testEachSharedTemplateIsRenamedByOneKey() {
        val (baseKey, subKey) = addSharedTemplate()
        val processor = ScopedElementKeyRenameProcessor()
        assertEquals(setOf(baseKey, subKey), ScopedElementKeyRenameProcessor.linkedKeys(subKey))
        val allRenames = LinkedHashMap<PsiElement, String>(mapOf(subKey to "x-b"))
        processor.prepareRenaming(subKey, "x-b", allRenames, subKey.useScope)
        assertEquals(setOf(baseKey, subKey), allRenames.keys)
        val templateReferences = allRenames.keys
            .flatMap { processor.findReferences(it, it.useScope, false) }
            .filter { it.element is JSStringTemplateExpression }
        assertEquals(1, templateReferences.size)
    }

    fun testRenameToAnInvalidTagNameIsRefused() {
        val validator = ScopedElementKeyRenameValidator()
        val key = myElKey()
        assertTrue(validator.pattern.accepts(key))
        assertFalse(validator.pattern.accepts(PsiTreeUtil.getParentOfType(key, JSClass::class.java)))
        assertNull(validator.getErrorMessage("kid-el", project))
        assertEquals(
            "'Kid' is not a valid custom element name (lowercase, starting with a letter, with a hyphen)",
            validator.getErrorMessage("Kid", project),
        )
        assertNotNull(validator.getErrorMessage("kid", project))
        assertNotNull(validator.getErrorMessage("1-kid", project))
        // Registered in plugin.xml: the rename dialog finds it.
        assertFalse(RenameInputValidatorRegistry.getInputValidator(key)!!.value("Kid"))
        assertNotNull(RenameInputValidatorRegistry.getInputErrorValidator(key)!!.`fun`("Kid"))
        assertTrue(RenameInputValidatorRegistry.getInputValidator(key)!!.value("kid-el"))
    }
}
