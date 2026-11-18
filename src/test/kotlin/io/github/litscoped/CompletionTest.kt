package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "elements.js",
            """
            export class UiButton extends HTMLElement {}
            export class UiIcon extends HTMLElement {}
            export class UserCard extends HTMLElement {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "base.js",
            """
            import { UiIcon } from './elements.js';
            export class Base extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'ui-icon': UiIcon };
            }
            """.trimIndent(),
        )
    }

    private fun complete(template: String): List<String> {
        myFixture.configureByText(
            "my-el.js",
            """
            import { UiButton, UserCard } from './elements.js';
            import { Base } from './base.js';
            class MyEl extends Base {
              static scopedElements = { ...super.scopedElements, 'ui-button': UiButton, 'user-card': UserCard };
              render() { return html`$template`; }
            }
            """.trimIndent(),
        )
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty().filter { it.contains('-') }.sorted()
    }

    fun testCompletesScopedTags() {
        assertEquals(listOf("ui-button", "ui-icon", "user-card"), complete("<div><<caret></div>"))
    }

    fun testFiltersByPrefix() {
        assertEquals(listOf("ui-button", "ui-icon"), complete("<ui-<caret>"))
    }

    fun testSingleMatchIsInserted() {
        complete("<user-c<caret>")
        assertTrue(myFixture.editor.document.text, myFixture.file.text.contains("<user-card"))
    }

    fun testNoScopedTagsInAttributeValues() {
        assertEmpty(complete("<div class=\"ui-<caret>\"></div>"))
    }
}
