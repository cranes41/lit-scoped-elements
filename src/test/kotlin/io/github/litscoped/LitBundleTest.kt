package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.util.Properties

class LitBundleTest : BasePlatformTestCase() {

    fun testPluralsAndQuotes() {
        assertEquals("Fired in 1 place: 'x-ev'", LitBundle.message("gutter.dispatches.tooltip", 1, "x-ev"))
        assertEquals("Used in 3 templates: <x-el>", LitBundle.message("gutter.tag.usages.tooltip", 3, "x-el"))
        assertTrue(LitBundle.message("settings.event.helpers.comment").contains("this.fire('my-event')"))
        assertEquals("Ignored tag names (regular expressions, e.g. 'sl-.*'):", LitBundle.message("inspection.missing.option.ignored.tags"))
    }

    fun testEveryKeyOfThePluginDescriptorExists() {
        val bundle = Properties().apply { File("src/main/resources/messages/LitBundle.properties").reader().use(::load) }
        val descriptor = File("src/main/resources/META-INF/plugin.xml").readText()
        val keys = Regex("""(?:key|groupKey)="([^"]+)"""").findAll(descriptor).map { it.groupValues[1] }.toSet()
        assertTrue(keys.size >= 6)
        assertEmpty(keys.filterNot(bundle::containsKey))
    }
}
