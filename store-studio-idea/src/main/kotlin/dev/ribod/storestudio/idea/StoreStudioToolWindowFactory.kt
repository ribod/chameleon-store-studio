package dev.ribod.storestudio.idea

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import dev.ribod.storestudio.idea.ui.StoreStudioRootPanel

/**
 * Registers the "Store Studio" Tool Window in IntelliJ IDEA and Android Studio.
 *
 * Implements [DumbAware] so developers can inspect and verify marketing assets
 * even while indexing or project sync is ongoing.
 */
public class StoreStudioToolWindowFactory :
    ToolWindowFactory,
    DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val factory = ContentFactory.getInstance()
        val content = factory.createContent(null, "", false)
        val rootPanel = StoreStudioRootPanel(project, content)
        content.component = rootPanel
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }
}
