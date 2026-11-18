package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EventCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "events.js",
            "export class SelectEvent extends Event { constructor() { super('item-picked'); } }",
        )
        myFixture.addFileToProject(
            "elements.js",
            """
            import { SelectEvent } from './events.js';
            export class BaseItem extends HTMLElement {
              remove() { this.dispatchEvent(new Event('item-removed')); }
            }
            export class ChildEl extends BaseItem {
              select() { this.dispatchEvent(new CustomEvent('item-selected')); }
              pick() { this.dispatchEvent(new SelectEvent()); }
              ping() { this.fire('item-pinged'); }
            }
            export class Unrelated extends HTMLElement {
              f() { this.dispatchEvent(new CustomEvent('not-mine')); }
            }
            """.trimIndent(),
        )
    }

    private fun complete(tagMarkup: String): List<String> {
        myFixture.configureByText(
            "my-el.js",
            """
            import { ChildEl } from './elements.js';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`$tagMarkup`; }
            }
            """.trimIndent(),
        )
        myFixture.completeBasic()
        return myFixture.lookupElementStrings.orEmpty().filter { it.startsWith("@") }.sorted()
    }

    fun testEventsFiredByTheElementClass() {
        assertEquals(
            listOf("@item-picked", "@item-pinged", "@item-removed", "@item-selected"),
            complete("<child-el @<caret>></child-el>"),
        )
    }

    fun testPrefix() {
        assertEquals(listOf("@item-picked", "@item-pinged"), complete("<child-el @item-p<caret>></child-el>"))
    }

    fun testNothingForUnknownElements() {
        assertEmpty(complete("<div @<caret>></div>"))
    }
}
