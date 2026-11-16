package dev.ribod.storestudio.idea

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ribod.storestudio.idea.model.DiffChangeDto
import dev.ribod.storestudio.idea.model.DiffDocumentDto
import dev.ribod.storestudio.idea.model.DiffSummaryDto
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.ui.StoreDiffPanel
import dev.ribod.storestudio.idea.ui.diffRowLabel
import java.io.File

class StoreDiffPanelTest : BasePlatformTestCase() {

    private fun module(name: String, vararg ids: String) = ModuleStoreArtifacts(
        moduleName = name,
        moduleDir = File("/tmp/$name"),
        buildStoreDir = File("/tmp/$name/build/store"),
        diffDocument = DiffDocumentDto(
            summary = DiffSummaryDto(modified = ids.size),
            changes = ids.map { DiffChangeDto(type = "MODIFIED", id = it) },
        ),
    )

    fun testDiffRowLabelFormatsSlotAndLocale() {
        assertEquals("PHONE • en-US", diffRowLabel("PLAY_STORE/PHONE/en-US", false))
        assertEquals("Play • PHONE • en-US", diffRowLabel("PLAY_STORE/PHONE/en-US", true))
        assertEquals("App Store • TABLET • fr-FR", diffRowLabel("APP_STORE/TABLET/fr-FR", true))
        assertEquals("odd", diffRowLabel("odd", true))
        assertNull(diffRowLabel(null, true))
    }

    fun testAllModulesSelectionCombinesChangesFromEveryModule() {
        val panel = StoreDiffPanel(project) {}
        panel.updateArtifacts(listOf(module(":app", "home"), module(":wear", "tile", "watch")))

        assertEquals(listOf(":app", ":wear", ":wear"), panel.listedChangeModules)
        assertTrue(panel.summaryText.startsWith("3 modified"))
    }

    fun testReloadKeepsTheSelectedChange() {
        val panel = StoreDiffPanel(project) {}
        panel.updateArtifacts(listOf(module(":app", "home"), module(":wear", "tile", "watch")))
        panel.selectChange(":wear", "watch")

        // A Gradle write delivers a fresh snapshot with new artifact instances
        panel.updateArtifacts(listOf(module(":app", "home"), module(":wear", "tile", "watch")))

        assertEquals(":wear/watch", panel.selectedChangeKey)
    }
}
