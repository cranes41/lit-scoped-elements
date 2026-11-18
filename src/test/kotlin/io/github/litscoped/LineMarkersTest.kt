package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LineMarkersTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "child.js",
            """
            export class ChildEl extends HTMLElement {
              select() { this.dispatchEvent(new CustomEvent('item-selected')); }
            }
            """.trimIndent(),
        )
    }

    private fun gutterTooltips(text: String): List<String> {
        myFixture.configureByText("my-el.js", text)
        return myFixture.findAllGutters().mapNotNull { it.tooltipText }.sorted()
    }

    fun testScopedElementsKeyAndTemplateEvents() {
        val tooltips = gutterTooltips(
            """
            import { ChildEl } from './child.js';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl, 'unused-el': ChildEl };
              render() {
                return html`
                  <child-el @item-selected=${"$"}{h}></child-el>
                  <child-el></child-el>
                `;
              }
            }
            """.trimIndent(),
        )
        assertTrue(tooltips.toString(), "Used in 2 templates: <child-el>" in tooltips)
        assertFalse(tooltips.toString(), tooltips.any { "unused-el" in it })
        // @event in the injected HTML.
        assertTrue(tooltips.toString(), "Fired in 1 place: 'item-selected'" in tooltips)
    }

    fun testDispatchAndListenerNames() {
        val tooltips = gutterTooltips(
            """
            class L extends LitElement {
              connectedCallback() { this.addEventListener('item-selected', h); }
              f() { this.dispatchEvent(new CustomEvent('nobody-listens')); }
            }
            """.trimIndent(),
        )
        assertTrue(tooltips.toString(), "Fired in 1 place: 'item-selected'" in tooltips)
        assertFalse(tooltips.toString(), tooltips.any { "nobody-listens" in it })

        myFixture.configureFromTempProjectFile("child.js")
        val childTooltips = myFixture.findAllGutters().mapNotNull { it.tooltipText }
        assertTrue(childTooltips.toString(), "Listened to in 1 place: 'item-selected'" in childTooltips)
    }
}
