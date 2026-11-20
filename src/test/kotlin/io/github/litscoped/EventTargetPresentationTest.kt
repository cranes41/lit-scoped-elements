package io.github.litscoped

import com.intellij.navigation.NavigationItem
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Event targets in JS code show their call, file and line in the "Choose Declaration" popup. */
class EventTargetPresentationTest : BasePlatformTestCase() {

    fun testListenersAndDispatchesArePresentedWithTheirCall() {
        myFixture.addFileToProject(
            "panel.js",
            "class Panel extends HTMLElement {\n  connectedCallback() {\n    this.addEventListener('picked', (e) => {});\n  }\n}",
        )
        myFixture.addFileToProject(
            "item.js",
            "class Item extends HTMLElement {\n  pick() {\n    this.dispatchEvent(new CustomEvent('picked', { bubbles: true }));\n  }\n}",
        )

        val listeners = LitEvents.findListeners(project, "picked").map(::describe)
        assertEquals(listOf("this.addEventListener('picked', …) panel.js:3"), listeners)

        val dispatches = LitEvents.findDispatches(project, "picked").map(::describe)
        assertEquals(listOf("new CustomEvent('picked', …) item.js:3"), dispatches)
    }

    private fun describe(target: com.intellij.psi.PsiElement): String {
        val presentation = (target as NavigationItem).presentation!!
        return "${presentation.presentableText} ${presentation.locationString}"
    }
}
