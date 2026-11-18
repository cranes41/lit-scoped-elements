package io.github.litscoped

import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class TemplateLocationTest : BasePlatformTestCase() {

    private fun template(): JSStringTemplateExpression =
        PsiTreeUtil.findChildrenOfType(myFixture.file, JSStringTemplateExpression::class.java)
            .first { ScopedElements.isLitHtmlTemplate(it) }

    fun testQuotedValuesDoNotBreakBindings() {
        myFixture.configureByText(
            "a.js",
            "const t = html`<x-el title=\"a<b>c\" data-x='@fake=1' @evt=${'$'}{h}></x-el>`;",
        )
        val bindings = LitEvents.findEventBindings(template())
        assertEquals(listOf("evt"), bindings.map { it.eventName })
        assertEquals("x-el", bindings.single().tagName)
    }

    fun testPresentationShowsTheWholeOpeningTag() {
        myFixture.configureByText(
            "a.js",
            "const t = html`<x-el title=\"a<b>c\"\n    @evt=${'$'}{h}></x-el>`;",
        )
        val binding = LitEvents.findEventBindings(template()).single()
        val location = TemplateLocation(template(), binding.range, binding.tagStart)
        assertEquals("<x-el title=\"a<b>c\" @evt=\${h}>", location.presentableText)
        assertEquals("a.js:2", location.locationString) // the binding is on the second line
        assertEquals("@evt", location.text)
    }

    fun testRangeFollowsEdits() {
        myFixture.configureByText("a.js", "const t = html`<div @evt=${'$'}{h}></div>`;")
        val binding = LitEvents.findEventBindings(template()).single()
        val location = TemplateLocation(template(), binding.range, binding.tagStart)
        val before = location.textOffset

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, "// a comment\n")
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        assertEquals(before + "// a comment\n".length, location.textOffset)
        assertEquals("@evt", location.text)
        assertTrue(location.isValid)
    }

    fun testRemovedTemplateIsInvalidWithoutErrors() {
        myFixture.configureByText("a.js", "const t = html`<div @evt=${'$'}{h}></div>`;\nconst after = 1;")
        val binding = LitEvents.findEventBindings(template()).single()
        val location = TemplateLocation(template(), binding.range, binding.tagStart)

        WriteCommandAction.runWriteCommandAction(project) {
            val document = myFixture.editor.document
            document.deleteString(0, document.text.indexOf("\n") + 1)
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        assertFalse(location.isValid)
        // Reading it must not throw.
        location.text
        location.presentableText
        location.locationString
    }
}
