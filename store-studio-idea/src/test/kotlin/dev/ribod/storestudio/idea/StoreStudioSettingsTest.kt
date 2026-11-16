package dev.ribod.storestudio.idea

import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xmlb.XmlSerializer
import dev.ribod.storestudio.idea.settings.StoreStudioSettings
import dev.ribod.storestudio.idea.settings.StoreStudioState

class StoreStudioSettingsTest : BasePlatformTestCase() {

    fun testLoadsSettingsWrittenByPreviousVersion() {
        // Format produced by the former PersistentStateComponent<StoreStudioSettings> bean
        val legacy = JDOMUtil.load(
            """
            <component name="StoreStudioSettings">
              <option name="activeTab" value="2" />
              <option name="autoReloadOnBuild" value="false" />
              <option name="defaultTargetFilter" value="APP_STORE" />
              <option name="gridSpacing" value="24" />
              <option name="showEngineBadges" value="false" />
              <option name="thumbnailScale" value="150" />
            </component>
            """.trimIndent(),
        )

        val settings = StoreStudioSettings()
        settings.loadState(XmlSerializer.deserialize(legacy, StoreStudioState::class.java))

        assertFalse(settings.autoReloadOnBuild)
        assertFalse(settings.showEngineBadges)
        assertTrue(settings.showValidationBadges)
        assertEquals("APP_STORE", settings.defaultTargetFilter)
    }

    fun testServiceIsRegisteredOnceAsLightService() {
        assertSame(
            StoreStudioSettings.getInstance(project),
            StoreStudioSettings.getInstance(project),
        )
        assertEquals("ALL", StoreStudioSettings.getInstance(project).defaultTargetFilter)
    }
}
