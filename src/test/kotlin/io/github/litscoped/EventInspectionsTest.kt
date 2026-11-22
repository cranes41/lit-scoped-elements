package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EventInspectionsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "emitter.js",
            """
            class Emitter extends LitElement {
              a() { this.dispatchEvent(new CustomEvent('item-selected')); }
              b() { this.fire('item-fired'); }
            }
            document.addEventListener('heard-elsewhere', () => {});
            """.trimIndent(),
        )
    }

    fun testUnknownEvent() {
        myFixture.enableInspections(UnknownEventInspection())
        myFixture.configureByText(
            "listener.js",
            """
            class L extends LitElement {
              render() {
                return html`<div @click=${"$"}{h} @item-selected=${"$"}{h} @item-fired=${"$"}{h} <weak_warning descr="Nothing in the project fires 'item-selcted'">@item-selcted</weak_warning>=${"$"}{h}></div>`;
              }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting(false, false, true)
    }

    fun testLongEventNameIsFound() {
        val name = "x-" + "long-".repeat(20) + "event"
        // Fired by a helper call only: dispatchEvent(new CustomEvent(…)) would also index the file as `dyn`.
        myFixture.addFileToProject("long.js", "class E extends LitElement { a() { this.fire('$name'); } }")
        myFixture.enableInspections(UnknownEventInspection())
        myFixture.configureByText("listener.js", "class L extends LitElement { render() { return html`<div @$name=${"$"}{h}></div>`; } }")
        myFixture.checkHighlighting(false, false, true)
    }

    fun testIgnoredEvents() {
        myFixture.enableInspections(UnknownEventInspection().apply { ignoredEvents = mutableListOf("sl-.*") })
        myFixture.configureByText("listener.js", "class L extends LitElement { render() { return html`<div @sl-change=${"$"}{h}></div>`; } }")
        myFixture.checkHighlighting(false, false, true)
    }

    fun testUnheardEvent() {
        myFixture.enableInspections(UnheardEventInspection())
        myFixture.configureByText(
            "emitter2.js",
            """
            class E extends LitElement {
              a() { this.dispatchEvent(new CustomEvent('heard-elsewhere')); }
              b() { this.dispatchEvent(new CustomEvent(<weak_warning descr="Nothing in the project listens to 'nobody-cares'">'nobody-cares'</weak_warning>)); }
            }
            """.trimIndent(),
        )
        myFixture.checkHighlighting(false, false, true)
    }
}
