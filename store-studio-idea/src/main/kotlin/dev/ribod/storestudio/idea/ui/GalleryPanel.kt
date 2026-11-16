package dev.ribod.storestudio.idea.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.gradle.StoreGradleTaskRunner
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.StoreAssetCard
import dev.ribod.storestudio.idea.navigation.StoreStudioNavigator
import dev.ribod.storestudio.idea.publish.StorePublishDialog
import dev.ribod.storestudio.idea.settings.StoreStudioSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Interactive visual screenshot gallery tab in the Store Studio tool window.
 *
 * Provides responsive multi-module, store, device, and locale filtering
 * over the native Java2D canvas.
 */
public class GalleryPanel(
    private val project: Project,
    private val onRefreshRequested: () -> Unit,
) : SimpleToolWindowPanel(true, true) {

    private val canvas = StoreGalleryCanvas(project) { card ->
        StoreStudioNavigator.navigate(
            project = project,
            sourceLocation = card.sourceLocation,
            functionFqn = card.functionFqn,
            functionName = card.functionName,
            annotationId = card.id,
        )
    }

    private var currentModules: List<ModuleStoreArtifacts> = emptyList()

    // Filter controls
    private val moduleCombo = ComboBox(arrayOf("ALL"))
    private val targetCombo = ComboBox(arrayOf("ALL", "PLAY_STORE", "APP_STORE"))
    private val deviceCombo = ComboBox(arrayOf("ALL"))
    private val localeCombo = ComboBox(arrayOf("ALL"))

    init {
        targetCombo.selectedItem = StoreStudioSettings.getInstance(project).defaultTargetFilter

        val scrollPane = JBScrollPane(canvas).apply {
            border = JBUI.Borders.empty()
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBar.unitIncrement = 16
        }
        setContent(scrollPane)

        setupToolbar()
    }

    private fun setupToolbar() {
        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
            isOpaque = false

            add(JLabel("Module:"))
            add(moduleCombo)
            add(JLabel("Store:"))
            add(targetCombo)
            add(JLabel("Device:"))
            add(deviceCombo)
            add(JLabel("Locale:"))
            add(localeCombo)
        }

        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("Zoom In", "Zoom in preview canvas", AllIcons.General.ZoomIn) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    canvas.zoomIn()
                }
            })
            add(object : AnAction("Zoom Out", "Zoom out preview canvas", AllIcons.General.ZoomOut) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    canvas.zoomOut()
                }
            })
            add(object : AnAction(
                "Reset Zoom",
                "Reset canvas zoom to 100%",
                AllIcons.General.ActualZoom,
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    canvas.resetZoom()
                }
            })
            addSeparator()
            add(
                object : AnAction(
                    "Refresh",
                    "Run Gradle (storeExport, storeParseMetadata) to regenerate the artifacts, then reload",
                    AllIcons.Actions.Refresh,
                ) {
                    override fun getActionUpdateThread(): ActionUpdateThread =
                        ActionUpdateThread.BGT
                    override fun actionPerformed(e: AnActionEvent) {
                        // One module shown: run there; otherwise on the whole build. The runner reloads when done.
                        val path = currentModules.singleOrNull()?.moduleDir?.absolutePath
                            ?: project.basePath
                        if (path ==
                            null
                        ) {
                            onRefreshRequested()
                        } else {
                            StoreGradleTaskRunner.regenerate(project, path)
                        }
                    }
                },
            )
            add(object : AnAction(
                "Publish...",
                "Publish assets to Google Play / App Store",
                AllIcons.Actions.Upload,
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    StorePublishDialog(project, currentModules).show()
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "StoreStudioGallery",
            actionGroup,
            true,
        )
        toolbar.targetComponent = this

        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(filterPanel, BorderLayout.WEST)
            add(toolbar.component, BorderLayout.EAST)
        }

        setToolbar(headerPanel)

        val filterListener = java.awt.event.ActionListener { applyFilters() }

        moduleCombo.addActionListener(filterListener)
        targetCombo.addActionListener(filterListener)
        deviceCombo.addActionListener(filterListener)
        localeCombo.addActionListener(filterListener)
    }

    public fun updateArtifacts(modules: List<ModuleStoreArtifacts>, cards: List<StoreAssetCard>) {
        this.currentModules = modules

        // Update module options
        val moduleNames = listOf("ALL") + modules.map { it.moduleName }.distinct()
        moduleCombo.replaceOptionsKeepingSelection(moduleNames)

        // Update device options
        val deviceNames =
            listOf("ALL") + cards.map { it.deviceSlot }.filter { it.isNotBlank() }.distinct()
        deviceCombo.replaceOptionsKeepingSelection(deviceNames)

        // Update locale options
        val localeNames =
            listOf("ALL") + cards.map { it.storeLocale }.filter { it.isNotBlank() }.distinct()
        localeCombo.replaceOptionsKeepingSelection(localeNames)

        canvas.setCards(cards)
        // Keep the visible filter selection in effect after a reload
        applyFilters()
    }

    private fun applyFilters() {
        canvas.filterCards(
            moduleFilter = moduleCombo.selectedItem as? String ?: "ALL",
            targetFilter = targetCombo.selectedItem as? String ?: "ALL",
            deviceFilter = deviceCombo.selectedItem as? String ?: "ALL",
            localeFilter = localeCombo.selectedItem as? String ?: "ALL",
        )
    }
}
