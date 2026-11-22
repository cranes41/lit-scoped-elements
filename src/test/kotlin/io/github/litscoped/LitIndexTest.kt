package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex

class LitIndexTest : BasePlatformTestCase() {

    fun testKeysOfAFile() {
        val file = myFixture.configureByText(
            "a.js",
            """
            import { NAME } from './names.js';
            class A extends LitElement {
              render() { return html`<x-el @picked=${"$"}{h}></x-el><div @click=${"$"}{h}></div>`; }
              f() {
                this.dispatchEvent(new CustomEvent('picked'));
                this.dispatchEvent(new Event(NAME));
                this.addEventListener(`tpl-event`, h);
                console.log('a message with spaces');
              }
            }
            const notLit = css`<y-el></y-el>`;
            """.trimIndent(),
        )
        // Not indexed: the css`` template, the message with spaces, the import path.
        assertEquals(
            setOf("tag:x-el", "bind:picked", "bind:click", "str:picked", "str:tpl-event", "dyn"),
            LitIndex.keysOf(file),
        )
    }

    fun testSearchUsesTheIndex() {
        myFixture.addFileToProject("with.js", "class A extends LitElement { render() { return html`<x-el></x-el>`; } }")
        myFixture.addFileToProject("without.js", "const s = '<x-el>';")
        val files = FileBasedIndex.getInstance()
            .getContainingFiles(LitIndex.NAME, LitIndex.tag("x-el"), ProjectSearch.scope(project))
            .map { it.name }
        assertEquals(listOf("with.js"), files)
    }

    fun testLongEventNamesAreIndexed() {
        val name = "my-app:" + "very-long-event-name-".repeat(10) + "changed"
        assertTrue(name.length > 200)
        val file = myFixture.configureByText("a.js", "this.dispatchEvent(new CustomEvent('$name'));")
        assertContainsElements(LitIndex.keysOf(file), LitIndex.string(name))
    }
}
