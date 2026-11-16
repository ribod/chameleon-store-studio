package dev.ribod.storestudio.idea

import com.intellij.openapi.ui.ComboBox
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.UIUtil
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.publish.StorePublishDialog
import dev.ribod.storestudio.idea.publish.buildPublishTaskOptions
import java.io.File
import javax.swing.JComponent

class StorePublishDialogTest : BasePlatformTestCase() {

    fun testMapsDialogSelectionToStorePublishTaskOptions() {
        assertEquals(
            listOf("--target=PLAY_STORE", "--track=beta", "--publish-dry-run"),
            buildPublishTaskOptions("PLAY_STORE", "beta", isDryRun = true),
        )
        assertEquals(
            listOf("--target=APP_STORE", "--track=internal"),
            buildPublishTaskOptions("APP_STORE", "internal", isDryRun = false),
        )
        // ALL publishes every configured marketplace, which is the task default
        assertEquals(
            listOf("--track=production"),
            buildPublishTaskOptions(
                "ALL (Google Play + App Store)",
                "production",
                isDryRun = false,
            ),
        )
    }

    fun testOkPublishesTheSelectionMadeInTheDialog() {
        var launched: Pair<String, List<String>>? = null
        val module = ModuleStoreArtifacts(
            moduleName = ":app",
            moduleDir = File("/tmp/app"),
            buildStoreDir = File("/tmp/app/build/store"),
        )
        val dialog = StorePublishDialog(project, listOf(module)) { path, options ->
            launched = path to options
        }
        try {
            val content = dialog.form
            comboWith(content, "PLAY_STORE").selectedItem = "PLAY_STORE"
            comboWith(content, "beta").selectedItem = "beta"
            checkBox(content, "Dry-run only").isSelected = false
            // Publishing for real stays blocked until the release is confirmed
            assertFalse(dialog.isOKActionEnabled)
            checkBox(content, "I confirm").isSelected = true
            assertTrue(dialog.isOKActionEnabled)

            dialog.performOKAction()
        } finally {
            if (!dialog.isDisposed) dialog.close(0)
        }

        assertEquals("/tmp/app" to listOf("--target=PLAY_STORE", "--track=beta"), launched)
    }

    private fun comboWith(root: JComponent, item: String): ComboBox<*> =
        UIUtil.findComponentsOfType(root, ComboBox::class.java).single { combo ->
            (0 until combo.itemCount).any { combo.getItemAt(it) == item }
        }

    private fun checkBox(root: JComponent, textPrefix: String): JBCheckBox =
        UIUtil.findComponentsOfType(root, JBCheckBox::class.java).single {
            it.text.startsWith(textPrefix)
        }
}
