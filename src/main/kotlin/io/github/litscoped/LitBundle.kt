package io.github.litscoped

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.LitBundle"

/** User-visible texts, in `messages/LitBundle.properties` so the plugin can be translated. */
object LitBundle {
    private val instance = DynamicBundle(LitBundle::class.java, BUNDLE)

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        instance.getMessage(key, *params)
}
