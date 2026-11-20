package io.github.litscoped

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecmal4.JSSuperExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptPropertySignature
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

/**
 * File-based index of what the plugin searches for, so searches open only the files that can match.
 *
 * Keys of a JS/TS file:
 * - `tag:<name>`   a custom element tag opened in a Lit `html` template
 * - `bind:<event>` an `@event` binding in a Lit template
 * - `str:<value>`  an event-name-like string literal passed as first argument of a call or `new`
 *                  (`new CustomEvent('x')`, `addEventListener('x')`, `super('x')`, `customElements.define('x', …)`),
 *                  an event declared in a JSDoc `@fires x` tag, or an entry of a TypeScript `…EventMap` interface
 * - `reg:<name>`   an object key named like a custom element (`{ 'x-el': XEl }`), maybe a scopedElements entry
 * - `dyn`          an event-related call whose first argument is not a literal (`new Event(PANEL_CLOSE)`),
 *                  so the name can only be known by resolving it
 *
 * The indexer only reads the file's own syntax: it never resolves references.
 */
class LitIndex : ScalarIndexExtension<String>() {

    override fun getName(): ID<String, Void> = NAME

    override fun getVersion(): Int = 4

    override fun dependsOnFileContent(): Boolean = true

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter = FileBasedIndex.InputFilter { file ->
        val type = file.fileType
        type is LanguageFileType && type.language.isKindOf(JavascriptLanguage)
    }

    override fun getIndexer(): DataIndexer<String, Void, FileContent> = DataIndexer { content ->
        keysOf(content.psiFile).associateWith { null }
    }

    companion object {
        val NAME: ID<String, Void> = ID.create("io.github.litscoped.index")

        const val DYNAMIC = "dyn"
        fun tag(name: String) = "tag:$name"
        fun binding(event: String) = "bind:$event"
        fun string(value: String) = "str:$value"
        fun registration(tagName: String) = "reg:$tagName"

        private val EVENT_NAME_LIKE = Regex("""[A-Za-z_$][\w$:.\-]{0,63}""")
        private val EVENT_RELATED_CALLEE = Regex("""(?i).*(event|fire|emit|dispatch|listen).*""")

        fun keysOf(file: PsiFile): Set<String> {
            val keys = HashSet<String>()
            file.accept(object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    when (element) {
                        is JSStringTemplateExpression -> if (ScopedElements.isLitHtmlTemplate(element)) {
                            ScopedElements.computeCustomElementTags(element).filter { !it.closing }.forEach { keys += tag(it.name) }
                            LitEvents.computeEventBindings(element).forEach { keys += binding(it.eventName) }
                        }
                        is JSCallExpression -> indexCall(element, keys)
                        is JSProperty -> element.name?.takeIf(ScopedElements::isCustomElementName)?.let { keys += registration(it) }
                        is PsiComment -> LitEvents.documentedEventNames(element.text).forEach { keys += string(it.first) }
                        is TypeScriptPropertySignature -> if (LitEvents.isEventMapEntry(element)) {
                            element.name?.takeIf(EVENT_NAME_LIKE::matches)?.let { keys += string(it) }
                        }
                    }
                    super.visitElement(element)
                }
            })
            return keys
        }

        private fun indexCall(call: JSCallExpression, keys: MutableSet<String>) {
            val argument = call.arguments.firstOrNull() ?: return
            val value = LitEvents.literalString(argument)
            if (value != null) {
                if (EVENT_NAME_LIKE.matches(value)) keys += string(value)
            } else if (argument !is JSLiteralExpression) {
                // super(SelectEvent.NAME) in an Event subclass
                if (call.methodExpression is JSSuperExpression) {
                    keys += DYNAMIC
                    return
                }
                val callee = (call.methodExpression as? JSReferenceExpression)?.referencedName ?: return
                if (EVENT_RELATED_CALLEE.matches(callee)) keys += DYNAMIC
            }
        }
    }
}
