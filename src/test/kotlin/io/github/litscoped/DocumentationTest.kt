package io.github.litscoped

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.backend.documentation.DocumentationData
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DocumentationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("child.js", "export class ChildEl extends HTMLElement {}")
        myFixture.addFileToProject("global.js", "export class GlobalEl extends HTMLElement {}\ncustomElements.define('global-el', GlobalEl);")
        myFixture.addFileToProject(
            "my-el.js",
            """
            import { ChildEl } from './child.js';
            class MyEl extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`<child-el></child-el><global-el></global-el><div></div>`; }
            }
            """.trimIndent(),
        )
    }

    fun testProviderIsRegistered() {
        val providers = ExtensionPointName<DocumentationTargetProvider>("com.intellij.platform.backend.documentation.targetProvider").extensionList
        assertTrue(providers.any { it is ScopedTagDocumentationTargetProvider })
    }

    fun testScopedTag() {
        val html = documentation("<child-el")!!
        assertTrue(html, html.contains("<b>ChildEl</b> — child.js"))
        assertTrue(html, html.contains("MyEl.scopedElements"))
    }

    fun testGlobalTag() {
        val html = documentation("<global-el")!!
        assertTrue(html, html.contains("<b>GlobalEl</b> — global.js"))
        assertTrue(html, html.contains("global (customElements.define)"))
    }

    fun testNothingForPlainHtml() {
        assertNull(documentation("<div"))
    }

    /** Documentation at the tag after [marker], asked from the host file and from the injected HTML (must agree). */
    private fun documentation(marker: String): String? {
        val file = myFixture.configureFromTempProjectFile("my-el.js")
        val manager = InjectedLanguageManager.getInstance(project)
        val host = manager.getTopLevelFile(file)
        val offset = host.text.indexOf(marker) + 3
        val provider = ScopedTagDocumentationTargetProvider()
        val fromHost = provider.documentationTargets(host, offset).singleOrNull()
        val injected = manager.findInjectedElementAt(host, offset)!!
        val injectedFile = injected.containingFile
        val injectedOffset = injected.textRange.startOffset + 1 // inside the same tag name token
        val fromInjected = provider.documentationTargets(injectedFile, injectedOffset).singleOrNull()
        assertEquals(fromHost == null, fromInjected == null)
        val html = (fromHost?.computeDocumentation() as? DocumentationData)?.html
        assertEquals(html, (fromInjected?.computeDocumentation() as? DocumentationData)?.html)
        return html
    }
}
