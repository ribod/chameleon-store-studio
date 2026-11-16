package dev.ribod.storestudio.idea.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import dev.ribod.storestudio.idea.gradle.StoreGradleTaskRunner
import java.awt.Component
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Empty-state panel displayed in the Tool Window when no `build/store/` artifacts are found.
 *
 * Provides clear instructions and one-click quick actions to trigger Store Studio Gradle tasks.
 */
public class StoreSetupGuidePanel(
    private val project: Project,
    private val onRefreshRequested: () -> Unit,
) : JPanel(GridBagLayout()) {

    init {
        isOpaque = false
        val column = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            maximumSize = JBUI.size(460, Int.MAX_VALUE)

            add(
                JBLabel("No Store Studio Assets Found").apply {
                    font = JBFont.label().asBold().deriveFont(JBFont.label().size2D + 3f)
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )

            add(
                JBLabel(
                    "<html>Chameleon Store Studio visualizes marketing screenshots, store " +
                        "listing previews, compliance reports, and visual regression " +
                        "diffs for your Compose applications.<br/><br/>" +
                        "Apply the <code>dev.ribod.storestudio</code> Gradle plugin in " +
                        "your project and execute the export or validation tasks to " +
                        "inspect assets live here.</html>",
                ).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    alignmentX = Component.LEFT_ALIGNMENT
                    border = JBUI.Borders.empty(12, 0, 16, 0)
                },
            )

            val buttonRow = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                isOpaque = false
                alignmentX = Component.LEFT_ALIGNMENT

                val exportBtn = JButton("Run storeExport").apply {
                    addActionListener {
                        project.basePath?.let { basePath ->
                            StoreGradleTaskRunner.runTask(project, basePath, "storeExport")
                        }
                    }
                }
                val validateBtn = JButton("Run storeValidate").apply {
                    addActionListener {
                        project.basePath?.let { basePath ->
                            StoreGradleTaskRunner.runTask(project, basePath, "storeValidate")
                        }
                    }
                }
                val refreshBtn = JButton("Refresh").apply {
                    addActionListener {
                        onRefreshRequested()
                    }
                }

                add(exportBtn)
                add(Box.createHorizontalStrut(JBUI.scale(8)))
                add(validateBtn)
                add(Box.createHorizontalStrut(JBUI.scale(8)))
                add(refreshBtn)
            }
            add(buttonRow)

            add(Box.createVerticalStrut(JBUI.scale(20)))

            add(
                ActionLink("Open Chameleon Store Studio Documentation →") {
                    BrowserUtil.browse("https://github.com/ribod/chameleon-store-studio")
                }.apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )
        }

        add(column)
    }
}
