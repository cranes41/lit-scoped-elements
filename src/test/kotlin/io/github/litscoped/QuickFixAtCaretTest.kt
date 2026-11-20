package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Alt+Enter on a missing tag, caret inside the template, offers the fix (not only the whole-file list). */
class QuickFixAtCaretTest : BasePlatformTestCase() {

    fun testFixIsOfferedAtTheCaret() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.addFileToProject("ui-avatar.js", "export class UiAvatar extends LitElement {}")
        myFixture.configureByText(
            "my-app.js",
            """
            class MyApp extends ScopedElementsMixin(LitElement) {
              static scopedElements = {};
              render() { return html`<div><ui-av<caret>atar initials="XX"></ui-avatar></div>`; }
            }
            """.trimIndent(),
        )
        val intentions = myFixture.availableIntentions.map { it.text }
        assertTrue(intentions.toString(), "Add 'ui-avatar' to scopedElements" in intentions)
    }
}
