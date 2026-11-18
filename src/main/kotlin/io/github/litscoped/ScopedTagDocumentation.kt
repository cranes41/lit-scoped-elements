package io.github.litscoped

import com.intellij.icons.AllIcons
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil

/** Hover / quick documentation for custom element tags in Lit templates. */
class ScopedTagDocumentationTargetProvider : DocumentationTargetProvider {

    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        val manager = InjectedLanguageManager.getInstance(file.project)
        val (hostFile, hostOffset) =
            if (manager.isInjectedFragment(file)) manager.getTopLevelFile(file) to manager.injectedToHost(file, offset)
            else file to offset
        val element = hostFile.findElementAt(hostOffset) ?: return emptyList()
        val template = PsiTreeUtil.getParentOfType(element, JSStringTemplateExpression::class.java, false) ?: return emptyList()
        if (!ScopedElements.isLitHtmlTemplate(template)) return emptyList()
        val relative = hostOffset - template.textRange.startOffset
        val tag = ScopedElements.findCustomElementTags(template)
            .firstOrNull { relative >= it.range.startOffset && relative <= it.range.endOffset } ?: return emptyList()
        val target = ScopedTagDocumentationTarget(template, tag.name)
        return if (target.hasInformation()) listOf(target) else emptyList()
    }
}

/** What a tag resolves to: the registering `scopedElements` entries, or the global definitions. */
class ScopedTagDocumentationTarget(
    private val template: JSStringTemplateExpression,
    private val tagName: String,
) : DocumentationTarget {

    private data class Registration(val elementClass: String, val file: String?, val source: String)

    private fun registrations(): List<Registration> {
        val scoped = TemplateOwners.findOwnerClasses(template).flatMap { owner ->
            ScopedElements.getScopedElements(owner)[tagName].orEmpty().map { entry ->
                val cls = ScopedElements.resolveElementClass(entry) as? JSClass
                Registration(cls?.name ?: entry.value?.text ?: "?", cls?.containingFile?.name, "${owner.name}.scopedElements")
            }
        }
        if (scoped.isNotEmpty()) return scoped.distinct()
        return GlobalElements.findDefinitions(template.project, tagName).map { definition ->
            val cls = definition.elementClass
            val how = if (definition.site.text.startsWith("customElements") || definition.site.text.contains(".define(")) {
                "customElements.define"
            } else "@customElement"
            Registration(cls?.name ?: "?", cls?.containingFile?.name ?: definition.site.containingFile.name, "global ($how)")
        }.distinct()
    }

    fun hasInformation(): Boolean = registrations().isNotEmpty()

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val templatePointer = SmartPointerManager.createPointer(template)
        val tag = tagName
        return Pointer { templatePointer.element?.let { ScopedTagDocumentationTarget(it, tag) } }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder("<$tagName>").icon(AllIcons.Nodes.Tag).presentation()

    override fun computeDocumentationHint(): String {
        val first = registrations().firstOrNull() ?: return "&lt;$tagName&gt;"
        return "&lt;$tagName&gt; → <b>${esc(first.elementClass)}</b> (${esc(first.source)})"
    }

    override fun computeDocumentation(): DocumentationResult {
        val html = buildString {
            append(DocumentationMarkup.DEFINITION_START).append("&lt;").append(esc(tagName)).append("&gt;")
            append(DocumentationMarkup.DEFINITION_END)
            append(DocumentationMarkup.SECTIONS_START)
            for (registration in registrations()) {
                append(DocumentationMarkup.SECTION_HEADER_START).append("Class")
                append(DocumentationMarkup.SECTION_SEPARATOR)
                append("<b>").append(esc(registration.elementClass)).append("</b>")
                registration.file?.let { append(" — ").append(esc(it)) }
                append(DocumentationMarkup.SECTION_END)
                append(DocumentationMarkup.SECTION_HEADER_START).append("Registered in")
                append(DocumentationMarkup.SECTION_SEPARATOR).append(esc(registration.source))
                append(DocumentationMarkup.SECTION_END)
            }
            append(DocumentationMarkup.SECTIONS_END)
        }
        return DocumentationResult.documentation(html)
    }

    private fun esc(text: String) = StringUtil.escapeXmlEntities(text)
}
