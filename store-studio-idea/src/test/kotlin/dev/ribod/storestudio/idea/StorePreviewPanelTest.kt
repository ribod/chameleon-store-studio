package dev.ribod.storestudio.idea

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.StoreAssetCard
import dev.ribod.storestudio.idea.ui.StorePreviewPanel
import java.io.File

class StorePreviewPanelTest : BasePlatformTestCase() {

    private val module = ModuleStoreArtifacts(
        moduleName = ":app",
        moduleDir = File("/tmp/app"),
        buildStoreDir = File("/tmp/app/build/store"),
    )

    private fun card(id: String, device: String, locale: String) = StoreAssetCard(
        moduleName = ":app",
        slotKey = "PLAY_STORE/$device/$locale/$id",
        target = "PLAY_STORE",
        deviceSlot = device,
        storeLocale = locale,
        id = id,
        title = id,
        order = 0,
        imageFile = File("/tmp/app/build/store/export/$id-$device-$locale.png"),
        widthPx = 1080,
        heightPx = 1920,
        engine = "LAYOUTLIB",
    )

    // Every reload delivers new instances with the same content
    private fun cards() = listOf(
        card("home", "PHONE", "en-US"),
        card("home", "TABLET", "en-US"),
        card("home", "PHONE", "es-419"),
    )

    private fun combo(panel: StorePreviewPanel, item: String): ComboBox<*> =
        UIUtil.findComponentsOfType(panel, ComboBox::class.java).single { combo ->
            (0 until combo.itemCount).any { combo.getItemAt(it) == item }
        }

    fun testReloadWithUnchangedArtifactsDoesNotRebuildTheUi() {
        val panel = StorePreviewPanel(project)
        panel.updateArtifacts(listOf(module), cards())
        val afterFirst = panel.rebuildCount

        panel.updateArtifacts(listOf(module), cards())

        assertEquals(1, afterFirst)
        assertEquals(afterFirst, panel.rebuildCount)
    }

    fun testReloadKeepsTheSelectedFilters() {
        val panel = StorePreviewPanel(project)
        panel.updateArtifacts(listOf(module), cards())
        combo(panel, "es-419").selectedItem = "es-419"
        combo(panel, "TABLET").selectedItem = "TABLET"

        panel.updateArtifacts(listOf(module), cards())

        assertEquals("es-419", combo(panel, "es-419").selectedItem)
        assertEquals("TABLET", combo(panel, "TABLET").selectedItem)
    }

    fun testChangingAFilterRebuildsThePreview() {
        val panel = StorePreviewPanel(project)
        panel.updateArtifacts(listOf(module), cards())
        val before = panel.rebuildCount

        combo(panel, "TABLET").selectedItem = "TABLET"

        assertEquals(before + 1, panel.rebuildCount)
    }
}
