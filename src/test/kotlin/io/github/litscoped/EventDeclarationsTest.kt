package io.github.litscoped

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Event names held in objects, enums and `as const` values; events declared with JSDoc or an event map. */
class EventDeclarationsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "names.ts",
            """
            export const Events = { CHANGE: 'obj-change', NESTED: { OPEN: 'nested-open' } } as const;
            export enum EnumEvents { Close = 'enum-close' }
            export const SAVE = 'as-save' as const;
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "emitter.ts",
            """
            import { Events, EnumEvents, SAVE } from './names';
            /**
             * A documented element.
             * @fires doc-event - Fired when something happens
             * @fires {CustomEvent<string>} typed-doc-event - With a type
             * @event legacy-doc-event
             * @fires obj-change - Documented and dispatched: the dispatch wins
             */
            export class Emitter extends LitElement {
              a() { this.dispatchEvent(new CustomEvent(Events.CHANGE)); }
              b() { this.dispatchEvent(new CustomEvent(Events.NESTED.OPEN)); }
              c() { this.dispatchEvent(new CustomEvent(EnumEvents.Close)); }
              d() { this.dispatchEvent(new Event(SAVE)); }
            }
            customElements.define('x-emitter', Emitter);
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "event-map.ts",
            """
            declare global {
              interface HTMLElementEventMap { 'map-event': CustomEvent<number>; }
            }
            export {};
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "listener.js",
            """
            class Listener extends LitElement {
              render() {
                return html`<x-emitter @obj-change=${"$"}{h} @nested-open=${"$"}{h} @enum-close=${"$"}{h} @as-save=${"$"}{h}
                  @doc-event=${"$"}{h} @typed-doc-event=${"$"}{h} @legacy-doc-event=${"$"}{h} @map-event=${"$"}{h}
                  @nowhere-event=${"$"}{h}></x-emitter>`;
              }
            }
            """.trimIndent(),
        )
    }

    fun testNamesFromObjectsEnumsAndAsConst() {
        assertEquals(listOf("emitter.ts:Events.CHANGE"), dispatchesOf("@obj-change"))
        assertEquals(listOf("emitter.ts:Events.NESTED.OPEN"), dispatchesOf("@nested-open"))
        assertEquals(listOf("emitter.ts:EnumEvents.Close"), dispatchesOf("@enum-close"))
        assertEquals(listOf("emitter.ts:SAVE"), dispatchesOf("@as-save"))
    }

    fun testJsDocDeclarations() {
        assertEquals(listOf("emitter.ts:doc-event"), dispatchesOf("@doc-event"))
        assertEquals(listOf("emitter.ts:typed-doc-event"), dispatchesOf("@typed-doc-event"))
        assertEquals(listOf("emitter.ts:legacy-doc-event"), dispatchesOf("@legacy-doc-event"))
    }

    fun testEventMapDeclaration() {
        assertEquals(listOf("event-map.ts:'map-event'"), dispatchesOf("@map-event"))
    }

    fun testCompletionOffersDocumentedEvents() {
        myFixture.configureByText("c.js", "class C extends LitElement { render() { return html`<x-emitter @<caret>></x-emitter>`; } }")
        val items = myFixture.completeBasic()?.map { it.lookupString }.orEmpty()
        for (name in listOf("doc-event", "typed-doc-event", "legacy-doc-event", "obj-change", "enum-close", "as-save")) {
            assertTrue("$name in $items", items.any { it.endsWith(name) })
        }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    fun testUnknownEventInspectionKnowsDeclarations() {
        myFixture.enableInspections(UnknownEventInspection())
        myFixture.configureFromTempProjectFile("listener.js")
        val reported = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).mapNotNull { it.description }
            .filter { it.startsWith("Nothing in the project fires") }
        assertEquals(listOf("Nothing in the project fires 'nowhere-event'"), reported)
    }

    /** Dispatch targets of the `@event` binding at [marker] in listener.js, as "file:text". */
    private fun dispatchesOf(marker: String): List<String> {
        val file = myFixture.configureFromTempProjectFile("listener.js")
        val offset = file.text.indexOf("$marker=") + 2
        val targets = LitEventGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        val manager = InjectedLanguageManager.getInstance(project)
        return targets.orEmpty().map { "${manager.getTopLevelFile(it).name}:${it.text}" }.sorted()
    }
}
