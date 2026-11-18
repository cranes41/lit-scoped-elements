package io.github.litscoped

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LitSettingsTest : BasePlatformTestCase() {

    override fun tearDown() {
        try {
            LitSettings.getInstance(project).loadState(LitSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testDefaults() {
        val settings = LitSettings.getInstance(project)
        assertEquals(listOf("fire", "emit"), settings.eventHelpers)
        assertFalse(settings.searchLibraries)
    }

    fun testHelpersAreTrimmedAndDeduplicated() {
        val settings = LitSettings.getInstance(project)
        settings.eventHelpers = " fire , dispatchIt,, fire ".split(',')
        assertEquals(listOf("fire", "dispatchIt"), settings.eventHelpers)
    }

    fun testConfigurablePanel() {
        val configurable = LitSettingsConfigurable(project)
        try {
            assertNotNull(configurable.createComponent())
            configurable.reset()
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
        }
    }
}
