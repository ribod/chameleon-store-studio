package dev.ribod.storestudio.idea.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBTabbedPane
import dev.ribod.storestudio.idea.io.StoreArtifactsService
import dev.ribod.storestudio.idea.settings.StoreStudioSettingsListener
import java.awt.CardLayout
import javax.swing.JPanel

/**
 * Root container panel for Store Studio managing state transitions between
 * empty state setup guide and the 4 interactive feature tabs.
 */
public class StoreStudioRootPanel(private val project: Project, parentDisposable: Disposable) :
    JPanel() {

    private val cardLayout = CardLayout()
    private val emptyGuidePanel = StoreSetupGuidePanel(project) { reload() }

    private val galleryPanel = GalleryPanel(project) { reload() }
    private val previewPanel = StorePreviewPanel(project)
    private val diffPanel = StoreDiffPanel(project) { reload() }
    private val validationPanel = StoreValidationPanel(project) { reload() }

    private val tabbedPane = JBTabbedPane().apply {
        addTab("Gallery", galleryPanel)
        addTab("Store Preview", previewPanel)
        addTab("Changes & Diff", diffPanel)
        addTab("Validation", validationPanel)
    }

    private val artifacts = StoreArtifactsService.getInstance(project)

    init {
        layout = cardLayout
        add(emptyGuidePanel, CARD_EMPTY)
        add(tabbedPane, CARD_CONTENT)

        // Snapshots are delivered on the EDT for as long as the tool window content lives
        artifacts.addListener(parentDisposable, ::render)

        // Subscribe to settings changes
        project.messageBus.connect(parentDisposable).subscribe(
            StoreStudioSettingsListener.TOPIC,
            StoreStudioSettingsListener {
                repaint()
            },
        )

        reload()
    }

    public fun reload() {
        artifacts.requestReload()
    }

    private fun render(snapshot: StoreArtifactsService.Snapshot) {
        val loadedModules = snapshot.modules
        val hasAnyArtifacts = loadedModules.any { it.hasArtifacts }

        if (hasAnyArtifacts) {
            galleryPanel.updateArtifacts(loadedModules, snapshot.cards)
            previewPanel.updateArtifacts(loadedModules, snapshot.cards)
            diffPanel.updateArtifacts(loadedModules)
            validationPanel.updateArtifacts(loadedModules)
            cardLayout.show(this, CARD_CONTENT)
        } else {
            cardLayout.show(this, CARD_EMPTY)
        }
        revalidate()
        repaint()
    }

    private companion object {
        private const val CARD_EMPTY = "CARD_EMPTY"
        private const val CARD_CONTENT = "CARD_CONTENT"
    }
}
