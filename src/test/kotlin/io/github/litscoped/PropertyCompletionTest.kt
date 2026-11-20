package io.github.litscoped

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.injected.editor.EditorWindow
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PropertyCompletionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "elements.js",
            """
            export class BaseItem extends LitElement {
              static get properties() {
                return { label: { type: String } };
              }
            }
            export class ChildEl extends BaseItem {
              static properties = {
                userName: { type: String },
                open: { type: Boolean, reflect: true },
                items: { type: Array, attribute: false },
                maxCount: { type: Number, attribute: 'max-count' },
                _internal: { state: true },
              };
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "decorated.ts",
            """
            export class DecoratedEl extends LitElement {
              @property({ type: Boolean }) disabled = false;
              @property({ attribute: 'item-id' }) itemId?: string;
              @property({ attribute: false }) data: object = {};
              @property() size: 'small' | 'large' = 'small';
              @state() private _hidden = false;
              plain = 1;
            }
            """.trimIndent(),
        )
    }

    fun testPropertiesOfAClassAndItsSuperClasses() {
        val file = myFixture.configureFromTempProjectFile("elements.js")
        val child = PsiTreeUtil.findChildrenOfType(file, JSClass::class.java).single { it.name == "ChildEl" }
        assertEquals(
            listOf("items:-", "label:label", "maxCount:max-count", "open:open", "userName:username"),
            LitProperties.propertiesOf(child).map { "${it.name}:${it.attribute ?: "-"}" }.sorted(),
        )
    }

    fun testAttributesAndBindings() {
        assertEquals(
            listOf(".items", ".label", ".maxCount", ".open", ".userName", "?open", "label", "max-count", "open", "username"),
            complete("<child-el <caret>></child-el>"),
        )
    }

    fun testPropertyBindings() {
        assertEquals(listOf(".items", ".label", ".maxCount", ".open", ".userName"), complete("<child-el .<caret>></child-el>"))
    }

    fun testBooleanAttributes() {
        complete("<child-el ?<caret>></child-el>") // the only match: inserted
        assertEquals("<child-el ?open=${'$'}{}></child-el>", templateText())
    }

    fun testDecoratedProperties() {
        assertEquals(
            listOf(".data", ".disabled", ".itemId", ".size", "?disabled", "disabled", "item-id", "size"),
            complete("<decorated-el <caret>></decorated-el>"),
        )
    }

    fun testAlreadyBoundPropertiesAreSkipped() {
        assertEquals(
            listOf(".items", ".maxCount", ".userName", "max-count", "username"),
            complete("<child-el label=\"a\" ?open=${'$'}{x} <caret>></child-el>"),
        )
    }

    fun testNothingForUnknownElements() {
        assertEmpty(complete("<other-el .<caret>></other-el>"))
    }

    fun testInsertPropertyBinding() {
        complete("<child-el .user<caret>></child-el>")
        assertEquals("<child-el .userName=${'$'}{}></child-el>", templateText())
        assertEquals("<child-el .userName=${'$'}{".length, caretInTemplate())
    }

    fun testInsertAttribute() {
        complete("<child-el max-c<caret>></child-el>")
        assertEquals("<child-el max-count=\"\"></child-el>", templateText())
        assertEquals("<child-el max-count=\"".length, caretInTemplate())
    }

    /** Completes in a template of a class registering child-el and decorated-el; returns the property items. */
    private fun complete(markup: String): List<String> {
        myFixture.configureByText(
            "my-el.ts",
            """
            import { ChildEl } from './elements.js';
            import { DecoratedEl } from './decorated';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl, 'decorated-el': DecoratedEl };
              render() { return html`$markup`; }
            }
            """.trimIndent(),
        )
        val items = myFixture.completeBasic() ?: return emptyList() // single item: inserted
        return items.filter { it.getUserData(LitProperties.PROPERTY_ITEM) == true }.map { it.lookupString }.sorted()
    }

    private fun hostText(): String = InjectedLanguageManager.getInstance(project).getTopLevelFile(myFixture.file).text

    private fun templateText(): String {
        val text = hostText()
        return text.substring(text.indexOf("html`") + 5, text.lastIndexOf('`'))
    }

    private fun caretInTemplate(): Int {
        val editor = (myFixture.editor as? EditorWindow)?.delegate ?: myFixture.editor
        return editor.caretModel.offset - (hostText().indexOf("html`") + 5)
    }
}
