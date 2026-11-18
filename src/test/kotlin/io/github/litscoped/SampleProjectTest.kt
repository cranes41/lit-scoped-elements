package io.github.litscoped

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

/** Runs the plugin on the real sources of `sample-project/` and checks what its comments promise. */
class SampleProjectTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = File("sample-project").absolutePath

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("src", "src")
        myFixture.enableInspections(MissingScopedElementInspection())
    }

    private fun reportedTags(path: String): List<String> {
        myFixture.configureFromTempProjectFile(path)
        return myFixture.doHighlighting(HighlightSeverity.WARNING)
            .filter { it.description?.contains("scopedElements") == true }
            .map(HighlightInfo::getDescription)
            .map { Regex("<([^>]+)>").find(it)!!.groupValues[1] }
            .sorted()
    }

    fun testMyApp() = assertEquals(listOf("ui-avatar", "ui-tooltip"), reportedTags("src/my-app.js"))

    fun testUserCard() = assertEquals(listOf("ui-button"), reportedTags("src/features/user-card.js"))

    fun testTeamPanel() = assertEquals(listOf("ui-avatar", "ui-badge"), reportedTags("src/features/team-panel.js"))

    fun testDashboardTs() = assertEquals(listOf("ui-avatar"), reportedTags("src/features/my-dashboard.ts"))

    fun testEdgeCases() = assertEquals(listOf("sl-button", "ui-button"), reportedTags("src/features/edge-cases.js"))

    fun testScopedElementKeyToTemplates() {
        // MyApp's entry: its own template + the toolbar sub function it calls.
        assertEquals(listOf("my-app.js", "renderers.js"), usageFiles("src/my-app.js", "'ui-button': UiButton"))
        // BasePanel's entry: used by TeamPanel (spread), SimplePanel (inherited) and the toolbar called by TeamPanel.
        assertEquals(
            listOf("renderers.js", "team-panel.js", "team-panel.js", "team-panel.js"),
            usageFiles("src/features/base-panel.js", "'ui-button': UiButton"),
        )
    }

    private fun usageFiles(path: String, marker: String): List<String> {
        val file = myFixture.configureFromTempProjectFile(path)
        val offset = file.text.indexOf(marker) + 2
        return ScopedElementUsagesGotoHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
            .orEmpty()
            .map { it.containingFile.name }
            .sorted()
    }

    fun testSubRenderFunctions() {
        myFixture.configureFromTempProjectFile("src/features/renderers.js")
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("scopedElements") }
        assertEquals(
            listOf("Custom element <ui-tooltip> is not declared in MyApp.scopedElements, TeamPanel.scopedElements"),
            warnings,
        )
        val fixes = myFixture.getAllQuickFixes().map { it.text }.filter { it.startsWith("Add 'ui-tooltip'") }.sorted()
        assertEquals(listOf("Add 'ui-tooltip' to MyApp.scopedElements", "Add 'ui-tooltip' to TeamPanel.scopedElements"), fixes)

        assertTarget("src/features/renderers.js", "<ui-button>\${", "UiButton", "ui-button.js")
        assertTarget("src/features/renderers.js", "<user-card .user", "UserCard", "user-card.js")
        assertEquals(listOf("user-card.js:'user-selected'"), eventTargets("src/features/renderers.js", "@user-selected="))
    }

    fun testNavigationAcrossSample() {
        assertTarget("src/my-app.js", "<ui-button @click", "UiButton", "ui-button.js")
        assertTarget("src/my-app.js", "<user-card .user", "UserCard", "user-card.js")
        assertTarget("src/features/user-card.js", "<ui-badge>admin", "UiBadge", "ui-badge.js")   // via ...sharedElements
        assertTarget("src/features/team-panel.js", "<ui-button>Invite", "UiButton", "ui-button.js") // via ...super
        assertTarget("src/features/my-dashboard.ts", "<ui-badge>\${", "UiBadge", "ui-badge.js")
    }

    fun testEventNavigationAcrossSample() {
        // @event -> where it is fired
        assertEquals(listOf("user-card.js:'user-selected'"), eventTargets("src/my-app.js", "@user-selected="))
        assertEquals(listOf("base-panel.js:PANEL_CLOSE"), eventTargets("src/my-app.js", "@panel-close="))
        // new CustomEvent('user-selected') -> who listens
        assertEquals(
            listOf("my-app.js:@user-selected", "renderers.js:@user-selected", "team-panel.js:'user-selected'"),
            eventTargets("src/features/user-card.js", "'user-selected'"),
        )
        // addEventListener('user-selected') -> where it is fired
        assertEquals(listOf("user-card.js:'user-selected'"), eventTargets("src/features/team-panel.js", "'user-selected'"))
    }

    private fun eventTargets(path: String, marker: String): List<String> {
        val file = myFixture.configureFromTempProjectFile(path)
        val manager = InjectedLanguageManager.getInstance(project)
        val hostFile = manager.getTopLevelFile(file)
        // Last occurrence: the sample files mention the markers in their comments first.
        val offset = hostFile.text.lastIndexOf(marker) + 2
        return LitEventGotoDeclarationHandler().getGotoDeclarationTargets(hostFile.findElementAt(offset), offset, null)
            .orEmpty()
            .map { "${manager.getTopLevelFile(it).name}:${it.text.substringBefore('=')}" }
            .sorted()
    }

    private fun assertTarget(path: String, marker: String, className: String, fileName: String) {
        val file = myFixture.configureFromTempProjectFile(path)
        val hostFile = InjectedLanguageManager.getInstance(project).getTopLevelFile(file)
        val offset = hostFile.text.indexOf(marker) + 2
        val handler = ScopedElementGotoDeclarationHandler()
        val injected = InjectedLanguageManager.getInstance(project).findInjectedElementAt(hostFile, offset)
        for (element in listOfNotNull(hostFile.findElementAt(offset), injected)) {
            val target = handler.getGotoDeclarationTargets(element, offset, null)?.singleOrNull()
            assertTrue("$path $marker -> $target", target is JSClass)
            assertEquals(className, (target as JSClass).name)
            assertEquals(fileName, target.containingFile.name)
        }
    }
}
