package io.github.litscoped

import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** A template written in a base class is rendered by subclasses too, with their own scopedElements. */
class InheritedTemplateTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "elements.js",
            """
            export class ChildEl extends HTMLElement {}
            export class OtherChild extends HTMLElement {}
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "base.js",
            """
            import { ChildEl } from './elements.js';
            export class Base extends ScopedElementsMixin(LitElement) {
              static scopedElements = { 'child-el': ChildEl };
              render() { return html`<child-el></child-el>${"$"}{this.items.map((i) => html`<child-el></child-el>`)}`; }
            }
            """.trimIndent(),
        )
        myFixture.addFileToProject(
            "subs.js",
            """
            import { OtherChild } from './elements.js';
            import { Base } from './base.js';
            // Same tag, other class: uses Base.render() with its own registry.
            export class Remapped extends Base {
              static scopedElements = { 'child-el': OtherChild };
            }
            // Own registry without the tag, uses Base.render().
            export class Missing extends Base {
              static scopedElements = {};
            }
            // Calls super.render(): still renders Base's template.
            export class Wrapping extends Base {
              static scopedElements = {};
              render() { return html`<section>${"$"}{super.render()}</section>`; }
            }
            // Replaces render(): Base's template is never rendered.
            export class Replacing extends Base {
              static scopedElements = {};
              render() { return html`<p>own</p>`; }
            }
            // Below Replacing: inherits Replacing.render(), not Base's.
            export class BelowReplacing extends Replacing {
              static scopedElements = {};
            }
            // Below Remapped without its own render: Base's template again.
            export class BelowRemapped extends Remapped {}
            """.trimIndent(),
        )
    }

    fun testOwnersOfBaseTemplate() {
        val file = myFixture.configureFromTempProjectFile("base.js")
        val template = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(
            file, com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression::class.java,
        ).first { ScopedElements.isLitHtmlTemplate(it) }
        assertEquals(
            listOf("Base", "BelowRemapped", "Missing", "Remapped", "Wrapping"),
            TemplateOwners.findOwnerClasses(template).mapNotNull { it.name }.sorted(),
        )
    }

    fun testInspectionReportsSubclassesMissingTheTag() {
        myFixture.enableInspections(MissingScopedElementInspection())
        myFixture.configureFromTempProjectFile("base.js")
        val warnings = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.WARNING)
            .mapNotNull { it.description }
            .filter { it.contains("scopedElements") }
        // Both <child-el> tags: in render() and in the arrow callback inside it.
        assertEquals(
            List(2) { "Custom element <child-el> is not declared in Missing.scopedElements, Wrapping.scopedElements" },
            warnings,
        )
    }

    fun testTagNavigationListsEveryRegisteredClass() {
        val file = myFixture.configureFromTempProjectFile("base.js")
        val offset = file.text.indexOf("<child-el") + 2
        val targets = ScopedElementGotoDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        assertEquals(listOf("ChildEl", "OtherChild"), targets.orEmpty().map { (it as JSClass).name!! }.sorted())
    }

    fun testSubclassKeyFindsInheritedTemplate() {
        val file = myFixture.configureFromTempProjectFile("subs.js")
        val offset = file.text.indexOf("'child-el': OtherChild") + 2
        val targets = ScopedElementUsagesGotoHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, null)
        assertEquals(listOf("base.js", "base.js"), targets.orEmpty().map { it.containingFile.name })
    }
}
