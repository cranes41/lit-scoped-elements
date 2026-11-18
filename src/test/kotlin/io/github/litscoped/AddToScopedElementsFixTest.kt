package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AddToScopedElementsFixTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(MissingScopedElementInspection().apply { ignoreGlobalElements = false })
        myFixture.addFileToProject("components/x-button.js", "export class XButton extends HTMLElement {}")
        myFixture.addFileToProject(
            "other.js",
            """
            import { XButton } from './components/x-button.js';
            class Other extends ScopedElementsMixin(LitElement) { static scopedElements = { 'x-btn': XButton }; }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "components/g-el.js",
            "export class GlobalEl extends HTMLElement {}\ncustomElements.define('g-el', GlobalEl);",
        )
        myFixture.addFileToProject("components/my-card.ts", "export class MyCard extends HTMLElement {}")
        myFixture.addFileToProject("components/fancy-list.js", "export default class FancyList extends HTMLElement {}")
    }

    /** Import check that ignores spaces inside braces, which depend on the code style. */
    private fun assertImports(text: String, expected: String) {
        val normalized = text.replace(Regex("""\{\s*"""), "{ ").replace(Regex("""\s*\}"""), " }")
        assertTrue(text, normalized.contains(expected))
    }

    private fun applyFix(tag: String, header: String = "import { LitElement } from 'lit';"): String {
        myFixture.configureByText(
            "my-el.js",
            """
            $header
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {};
              render() { return html`<$tag></$tag>`; }
            }
            """.trimIndent(),
        )
        val offset = myFixture.file.text.indexOf("<$tag") + 2
        myFixture.editor.caretModel.moveToOffset(offset)
        myFixture.launchAction(myFixture.findSingleIntention("Add '$tag' to scopedElements"))
        return com.intellij.lang.injection.InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file).text
    }

    fun testClassRegisteredElsewhere() {
        val text = applyFix("x-btn")
        assertTrue(text, text.contains("'x-btn': XButton"))
        assertImports(text, "import { XButton } from './components/x-button.js';")
    }

    fun testGlobalDefinition() {
        val text = applyFix("g-el")
        assertTrue(text, text.contains("'g-el': GlobalEl"))
        assertImports(text, "import { GlobalEl } from './components/g-el.js';")
    }

    fun testClassNamedAfterTheTagInTypeScript() {
        val text = applyFix("my-card")
        assertTrue(text, text.contains("'my-card': MyCard"))
        assertImports(text, "import { MyCard } from './components/my-card.js';")
    }

    fun testDefaultExport() {
        val text = applyFix("fancy-list")
        assertImports(text, "import FancyList from './components/fancy-list.js';")
    }

    fun testExtensionlessStyleIsFollowed() {
        val text = applyFix("my-card", "import { helper } from './helpers';")
        assertImports(text, "import { MyCard } from './components/my-card';")
    }

    fun testExistingImportIsNotDuplicated() {
        val text = applyFix("x-btn", "import { XButton } from './components/x-button.js';")
        assertEquals(text, 1, Regex("import \\{\\s*XButton\\s*\\}").findAll(text).count())
    }

    fun testUnknownClassFallsBackToTheTagName() {
        val text = applyFix("no-such-el")
        assertTrue(text, text.contains("'no-such-el': NoSuchEl"))
        assertFalse(text, Regex("import.*NoSuchEl").containsMatchIn(text))
    }
}
