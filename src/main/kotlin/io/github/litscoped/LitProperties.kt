package io.github.litscoped

import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSParenthesizedExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSReturnStatement
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.ES6Decorator
import com.intellij.lang.javascript.psi.ecma6.TypeScriptAsExpression
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil

/**
 * A reactive property of a Lit element.
 *
 * @param attribute the attribute it is set from (lowercased name by default), null with `attribute: false`
 * @param type the `type` option (`String`, `Boolean`…) or the TypeScript type, when known
 */
data class LitProperty(
    val name: String,
    val attribute: String?,
    val type: String?,
    val owner: JSClass,
    val declaration: PsiElement,
) {
    val isBoolean: Boolean get() = type == "Boolean" || type == "boolean"
}

/**
 * Public reactive properties of Lit elements, declared with:
 * - `static properties = { name: { type: String } }`, or a `static get properties()` getter returning one
 * - the `@property({...})` decorator on a field or accessor
 *
 * Internal state (`state: true`, `@state()`, `@internalProperty()`) is left out: it can't be set from a template.
 */
object LitProperties {

    /** Marks the completion items for properties (used by tests). */
    val PROPERTY_ITEM: Key<Boolean> = Key.create("lit.scoped.propertyItem")

    private const val PROPERTIES = "properties"
    private val STATE_DECORATORS = setOf("state", "internalProperty")

    /** Properties of [cls] and its super classes; a redeclared property comes from the most derived class. */
    fun propertiesOf(cls: JSClass): List<LitProperty> = CachedValuesManager.getCachedValue(cls) {
        val result = LinkedHashMap<String, LitProperty>()
        val queue = ArrayDeque(listOf(cls))
        val visited = HashSet<JSClass>()
        while (queue.isNotEmpty() && visited.size < 32) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue
            ProgressManager.checkCanceled()
            for (property in ownProperties(current)) result.putIfAbsent(property.name, property)
            queue.addAll(ScopedElements.superClassesOf(current))
        }
        CachedValueProvider.Result.create(result.values.toList(), PsiModificationTracker.MODIFICATION_COUNT)
    }

    private fun ownProperties(cls: JSClass): List<LitProperty> = fromStaticProperties(cls) + fromDecorators(cls)

    private fun fromStaticProperties(cls: JSClass): List<LitProperty> {
        val objects = mutableListOf<JSObjectLiteralExpression>()
        cls.fields.filter { it.name == PROPERTIES }.mapNotNullTo(objects) { objectLiteral(it.initializer) }
        for (getter in cls.functions.filter { it.name == PROPERTIES && it.isGetProperty }) {
            PsiTreeUtil.findChildrenOfType(getter, JSReturnStatement::class.java)
                .filter { PsiTreeUtil.getParentOfType(it, JSFunction::class.java) == getter }
                .mapNotNullTo(objects) { objectLiteral(it.expression) }
        }
        return objects.flatMap { literal ->
            literal.properties.mapNotNull { entry ->
                val name = entry.name ?: return@mapNotNull null
                val options = objectLiteral(entry.value)
                if (options != null && isTrue(options.findProperty("state")?.value)) return@mapNotNull null
                property(name, options, null, cls, entry)
            }
        }
    }

    private fun fromDecorators(cls: JSClass): List<LitProperty> {
        val members: List<PsiElement> = cls.fields.toList() + cls.functions.filter { it.isGetProperty || it.isSetProperty }
        return members.mapNotNull { member ->
            val name = (member as? com.intellij.psi.PsiNamedElement)?.name ?: return@mapNotNull null
            val decorators = decoratorsOf(member)
            if (decorators.any { decoratorName(it) in STATE_DECORATORS }) return@mapNotNull null
            val decorator = decorators.firstOrNull { decoratorName(it) == "property" } ?: return@mapNotNull null
            val call = PsiTreeUtil.findChildOfType(decorator, JSCallExpression::class.java)
            val options = objectLiteral(call?.arguments?.firstOrNull())
            property(name, options, (member as? JSVariable)?.typeElement?.text, cls, member)
        }.distinctBy { it.name } // a getter and its setter
    }

    /** Decorators of a field or accessor: in its own attribute list or in its statement's. */
    private fun decoratorsOf(member: PsiElement): List<ES6Decorator> {
        val own = PsiTreeUtil.getChildrenOfTypeAsList(member, com.intellij.lang.javascript.psi.ecmal4.JSAttributeList::class.java)
            .flatMap { PsiTreeUtil.getChildrenOfTypeAsList(it, ES6Decorator::class.java) }
        if (own.isNotEmpty() || member is JSFunction) return own
        val statement = member.parent ?: return own
        return PsiTreeUtil.getChildrenOfTypeAsList(statement, com.intellij.lang.javascript.psi.ecmal4.JSAttributeList::class.java)
            .flatMap { PsiTreeUtil.getChildrenOfTypeAsList(it, ES6Decorator::class.java) }
    }

    private fun decoratorName(decorator: ES6Decorator): String? {
        val call = PsiTreeUtil.getChildOfType(decorator, JSCallExpression::class.java)
        val reference = (call?.methodExpression ?: PsiTreeUtil.getChildOfType(decorator, JSReferenceExpression::class.java))
        return (reference as? JSReferenceExpression)?.referenceName
    }

    private fun property(name: String, options: JSObjectLiteralExpression?, typeText: String?, owner: JSClass, declaration: PsiElement): LitProperty {
        val attributeValue = options?.findProperty("attribute")?.value
        val attribute = when {
            attributeValue == null -> name.lowercase()
            isFalse(attributeValue) -> null
            else -> LitEvents.literalString(attributeValue) ?: name.lowercase()
        }
        val type = options?.findProperty("type")?.value?.text ?: typeText
        return LitProperty(name, attribute, type, owner, declaration)
    }

    private fun objectLiteral(expression: PsiElement?): JSObjectLiteralExpression? = when (expression) {
        is JSObjectLiteralExpression -> expression
        is JSParenthesizedExpression -> objectLiteral(expression.innerExpression)
        is TypeScriptAsExpression -> objectLiteral(expression.expression)
        else -> null
    }

    private fun isTrue(expression: PsiElement?) = (expression as? JSLiteralExpression)?.text == "true"

    private fun isFalse(expression: PsiElement?) = (expression as? JSLiteralExpression)?.text == "false"
}
