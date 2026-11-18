package io.github.litscoped

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GlobalElementsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "global-button.js",
            """
            export class GlobalButton extends HTMLElement {
              press() { this.dispatchEvent(new CustomEvent('pressed')); }
            }
            customElements.define('global-button', GlobalButton);
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "decorated.ts",
            """
            @customElement('decorated-el')
            export class DecoratedEl extends LitElement {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "other-pressed.js",
            "class Other extends HTMLElement { f() { this.dispatchEvent(new CustomEvent('pressed')); } }",
        )
        myFixture.addFileToProject(
            "app.js",
            """
            class App extends ScopedElementsMixin(LitElement) {
              static scopedElements = {};
              render() {
                return html`<global-button @pressed=${"$"}{h}></global-button><decorated-el></decorated-el><missing-el></missing-el>`;
              }
            }
            class NotScoped extends LitElement {
              render() { return html`<global-button></global-button>`; }
            }
            """.trimIndent(),
        )
    }

    fun testDefinitions() {
        val define = GlobalElements.findDefinitions(project, "global-button").single()
        assertEquals("GlobalButton", define.elementClass?.name)
        val decorator = GlobalElements.findDefinitions(project, "decorated-el").single()
        assertEquals("DecoratedEl", decorator.elementClass?.name)
        assertEmpty(GlobalElements.findDefinitions(project, "missing-el"))
    }

    fun testTagNavigationFallsBackToGlobalDefinitions() {
        assertEquals("GlobalButton", navigate("<global-button @pressed"))
        assertEquals("DecoratedEl", navigate("<decorated-el"))
        assertEquals("GlobalButton", navigate("<global-button></global-button>`; }")) // class without scoped elements
    }

    fun testInspectionSkipsGlobalElementsByDefault() {
        myFixture.enableInspections(MissingScopedElementInspection())
        assertEquals(listOf("missing-el"), reportedTags())
    }

    fun testInspectionCanReportGlobalElements() {
        myFixture.enableInspections(MissingScopedElementInspection().apply { ignoreGlobalElements = false })
        assertEquals(listOf("decorated-el", "global-button", "missing-el"), reportedTags())
    }

    fun testEventNavigationUsesTheGlobalClass() {
        val file = myFixture.configureFromTempProjectFile("app.js")
        val offset = file.text.indexOf("@pressed") + 2
        val targets = LitEventGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        assertEquals(listOf("GlobalButton"), targets.orEmpty().map { PsiTreeUtil.getParentOfType(it, JSClass::class.java)?.name })
    }

    private fun navigate(marker: String): String? {
        val file = myFixture.configureFromTempProjectFile("app.js")
        val offset = file.text.indexOf(marker) + 2
        val targets = ScopedElementGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        return (targets?.single() as? JSClass)?.name
    }

    private fun reportedTags(): List<String> {
        myFixture.configureFromTempProjectFile("app.js")
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("scopedElements") }
            .map { Regex("<([^>]+)>").find(it)!!.groupValues[1] }
            .sorted()
    }
}
