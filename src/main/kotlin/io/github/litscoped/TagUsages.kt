package io.github.litscoped

import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil

/** Where a `scopedElements` entry is used: the matching tags in the templates the entry applies to. */
object TagUsages {

    /**
     * Calls [consumer] for every `<tagName>` and `</tagName>` in Lit templates whose `scopedElements` resolve
     * [tagName] to [property] (see [TemplateOwners]), in files of [scope] (all indexed files when null).
     */
    fun forEach(
        property: JSProperty,
        tagName: String,
        scope: SearchScope? = null,
        consumer: (JSStringTemplateExpression, TagOccurrence) -> Unit,
    ) {
        for (file in ProjectSearch.filesWithAnyKey(property.project, listOf(LitIndex.tag(tagName)))) {
            if (scope != null && !scope.contains(file.virtualFile ?: continue)) continue
            for (template in PsiTreeUtil.findChildrenOfType(file, JSStringTemplateExpression::class.java)) {
                ProgressManager.checkCanceled()
                if (!ScopedElements.isLitHtmlTemplate(template)) continue
                val tags = ScopedElements.findCustomElementTags(template).filter { it.name == tagName }
                if (tags.isEmpty()) continue
                val applies = TemplateOwners.findOwnerClasses(template)
                    .any { owner -> ScopedElements.getScopedElements(owner)[tagName].orEmpty().any { it.property == property } }
                if (applies) tags.forEach { consumer(template, it) }
            }
        }
    }

    /**
     * Replaces text in [template] without rebuilding it: each change is applied to the literal piece of the
     * template that contains it, so the template node stays valid and its formatting is kept.
     * [replacements] ranges are relative to [template] and must each lie inside one literal piece.
     */
    fun replaceInTemplate(template: JSStringTemplateExpression, replacements: List<Pair<TextRange, String>>) {
        val start = template.textRange.startOffset
        for (leaf in template.node.getChildren(null).filterIsInstance<LeafElement>()) {
            val leafRange = leaf.textRange.shiftLeft(start)
            val inside = replacements.filter { leafRange.contains(it.first) }.sortedByDescending { it.first.startOffset }
            if (inside.isEmpty()) continue
            var text = leaf.text
            for ((range, replacement) in inside) {
                text = text.replaceRange(range.startOffset - leafRange.startOffset, range.endOffset - leafRange.startOffset, replacement)
            }
            (leaf.psi as LeafPsiElement).replaceWithText(text)
        }
    }
}

/**
 * A tag name in a template (`ui-button` in `<ui-button>` or `</ui-button>`) referring to the `scopedElements`
 * key that registers it. Returned by [ScopedTagReferenceSearcher] for Find Usages and rename; renaming the key
 * rewrites the tag through the template's manipulator.
 */
class ScopedTagReference(
    template: JSStringTemplateExpression,
    range: TextRange,
    private val tagName: String,
) : PsiPolyVariantReferenceBase<JSStringTemplateExpression>(template, range, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        TemplateOwners.findOwnerClasses(element)
            .flatMap { ScopedElements.getScopedElements(it)[tagName].orEmpty() }
            .map { it.property }
            .distinct()
            .map { PsiElementResolveResult(it) }
            .toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement {
        TagUsages.replaceInTemplate(element, listOf(rangeInElement to newElementName))
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}

/**
 * Used for rename: one reference per template, renaming all its `<tagName>` and `</tagName>` at once. Separate
 * references per tag would each shift the offsets of the next ones in the same template.
 */
class ScopedTagTemplateRenameReference(
    template: JSStringTemplateExpression,
    firstRange: TextRange,
    private val tagName: String,
) : PsiPolyVariantReferenceBase<JSStringTemplateExpression>(template, firstRange, true) {

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ScopedTagReference(element, rangeInElement, tagName).multiResolve(incompleteCode)

    override fun handleElementRename(newElementName: String): PsiElement {
        val occurrences = ScopedElements.computeCustomElementTags(element).filter { it.name == tagName }
        TagUsages.replaceInTemplate(element, occurrences.map { it.range to newElementName })
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()
}
