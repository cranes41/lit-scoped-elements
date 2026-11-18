package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class UnusedScopedElementTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(UnusedScopedElementInspection())
        myFixture.addFileToProject("renderers.js", "export const renderBadge = () => html`<x-badge></x-badge>`;")
    }

    fun testReportsOnlyUnusedEntries() {
        myFixture.configureByText(
            "my-el.js",
            """
            import { renderBadge } from './renderers.js';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {
                'x-used': A,
                'x-badge': B,
                'x-in-sub': C,
                <warning descr="Scoped element <x-unused> is not used in any template">'x-unused'</warning>: D,
              };
              render() { return html`<x-used></x-used>${"$"}{renderBadge()}`; }
            }
            class SubEl extends MyEl {
              render() { return html`<x-in-sub></x-in-sub>`; }
            }
            const headers = { 'content-type': 'json' };
            """.trimIndent(),
        )
        myFixture.checkHighlighting()
    }

    fun testRemoveFix() {
        myFixture.configureByText(
            "my-el.js",
            """
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {
                'x-used': A,
                'x-un<caret>used': D,
              };
              render() { return html`<x-used></x-used>`; }
            }
            """.trimIndent(),
        )
        myFixture.launchAction(myFixture.findSingleIntention("Remove 'x-unused' from scopedElements"))
        myFixture.checkResult(
            """
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = {
                'x-used': A,
              };
              render() { return html`<x-used></x-used>`; }
            }
            """.trimIndent(),
        )
    }
}
