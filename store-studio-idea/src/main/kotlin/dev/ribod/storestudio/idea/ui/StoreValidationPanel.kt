package dev.ribod.storestudio.idea.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.gradle.StoreGradleTaskRunner
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.ViolationDto
import dev.ribod.storestudio.idea.navigation.StoreStudioNavigator
import dev.ribod.storestudio.idea.settings.StoreStudioTheme
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Store Compliance & Validation explorer in Store Studio.
 *
 * Audits Google Play and Apple App Store compliance violations and provides
 * direct jump-to-remediation.
 */
public class StoreValidationPanel(
    private val project: Project,
    private val onRefreshRequested: () -> Unit,
) : SimpleToolWindowPanel(true, true) {

    private var modules: List<ModuleStoreArtifacts> = emptyList()
    private var allViolations: List<ViolationDto> = emptyList()
    private var filteredViolations: List<ViolationDto> = emptyList()

    private val moduleSelector = ComboBox(arrayOf("ALL"))
    private val severitySelector = ComboBox(arrayOf("ALL", "ERROR", "WARNING", "INFO"))
    private val targetSelector = ComboBox(arrayOf("ALL", "PLAY_STORE", "APP_STORE"))

    private val statusSummaryLabel = JBLabel("Compliance Status")
    private val countsSummaryLabel = JBLabel("0 Violations")

    private val tableModel = ViolationTableModel()
    private val table = JBTable(tableModel)

    private val detailPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.empty(12, 16)
    }

    private val detailRuleId = JBLabel()
    private val detailMessage = JBLabel()
    private val detailHint = JBLabel()

    init {
        val splitter = JBSplitter(true, 0.65f).apply {
            firstComponent = buildTopPane()
            secondComponent = buildBottomPane()
            dividerWidth = JBUI.scale(4)
        }
        setContent(splitter)

        setupToolbar()
    }

    private fun setupToolbar() {
        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
            isOpaque = false
            add(JBLabel("Module:"))
            add(moduleSelector)
            add(JBLabel("Severity:"))
            add(severitySelector)
            add(JBLabel("Store:"))
            add(targetSelector)
        }

        val listener = java.awt.event.ActionListener { applyFilters() }
        moduleSelector.addActionListener(listener)
        severitySelector.addActionListener(listener)
        targetSelector.addActionListener(listener)

        val actionGroup = DefaultActionGroup().apply {
            add(
                object : AnAction(
                    "Run storeValidate",
                    "Run store compliance validation",
                    AllIcons.General.InspectionsOK,
                ) {
                    override fun getActionUpdateThread(): ActionUpdateThread =
                        ActionUpdateThread.BGT
                    override fun actionPerformed(e: AnActionEvent) {
                        val activeModule = getActiveModule()
                        val modulePath = activeModule?.moduleDir?.absolutePath
                            ?: project.basePath
                            ?: return
                        StoreGradleTaskRunner.runTask(project, modulePath, "storeValidate")
                    }
                },
            )
            add(object : AnAction("Refresh", "Reload validation report", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    onRefreshRequested()
                }
            })
        }

        val toolbar = ActionManager.getInstance()
            .createActionToolbar("StoreStudioValidation", actionGroup, true)
        toolbar.targetComponent = this

        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(filterPanel, BorderLayout.WEST)
            add(toolbar.component, BorderLayout.EAST)
        }

        setToolbar(headerPanel)
    }

    private fun buildTopPane(): JPanel {
        val pane = JPanel(BorderLayout())

        countsSummaryLabel.apply {
            font = StoreStudioTheme.subtitleFont
            foreground = StoreStudioTheme.textMuted
        }

        val summaryBanner = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(12, 16)
            isOpaque = false
            add(statusSummaryLabel.apply { font = StoreStudioTheme.titleFont }, BorderLayout.WEST)
            add(countsSummaryLabel, BorderLayout.EAST)
        }
        pane.add(summaryBanner, BorderLayout.NORTH)

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.rowHeight = JBUI.scale(26)
        table.setDefaultRenderer(
            Any::class.java,
            object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: javax.swing.JTable?,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int,
                ): Component {
                    val comp = super.getTableCellRendererComponent(
                        table,
                        value,
                        isSelected,
                        hasFocus,
                        row,
                        column,
                    ) as JLabel
                    if (column == 0 && value is String) {
                        when (value) {
                            "ERROR" -> {
                                comp.icon = AllIcons.General.Error
                                comp.foreground = StoreStudioTheme.errorFg
                            }

                            "WARNING" -> {
                                comp.icon = AllIcons.General.Warning
                                comp.foreground = StoreStudioTheme.warningFg
                            }

                            else -> {
                                comp.icon = AllIcons.General.Information
                                comp.foreground = StoreStudioTheme.textMuted
                            }
                        }
                    } else {
                        comp.icon = null
                        comp.foreground = if (isSelected) {
                            table?.selectionForeground
                        } else {
                            StoreStudioTheme.textPrimary
                        }
                    }
                    return comp
                }
            },
        )

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.selectedRow
                if (row >= 0 && row < filteredViolations.size) {
                    val violation = filteredViolations[row]
                    updateDetails(violation)
                    if (e.clickCount == 2) {
                        remediateViolation(violation)
                    }
                }
            }
        })

        val tableScroll = JBScrollPane(table).apply { border = JBUI.Borders.empty() }
        pane.add(tableScroll, BorderLayout.CENTER)
        return pane
    }

    private fun buildBottomPane(): JPanel {
        val pane = JPanel(BorderLayout())
        pane.border = JBUI.Borders.customLine(StoreStudioTheme.cardBorder, 1, 0, 0, 0)

        detailRuleId.apply {
            font = StoreStudioTheme.titleFont
            alignmentX = Component.LEFT_ALIGNMENT
        }
        detailMessage.apply {
            font = JBFont.label()
            foreground = StoreStudioTheme.textPrimary
            alignmentX = Component.LEFT_ALIGNMENT
        }
        detailHint.apply {
            font = StoreStudioTheme.subtitleFont
            foreground = StoreStudioTheme.textMuted
            alignmentX = Component.LEFT_ALIGNMENT
        }

        detailPanel.add(detailRuleId)
        detailPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        detailPanel.add(detailMessage)
        detailPanel.add(Box.createVerticalStrut(JBUI.scale(6)))
        detailPanel.add(detailHint)

        pane.add(detailPanel, BorderLayout.CENTER)
        return pane
    }

    public fun updateArtifacts(modules: List<ModuleStoreArtifacts>) {
        this.modules = modules
        val moduleNames = listOf("ALL") + modules.map { it.moduleName }.distinct()
        moduleSelector.model = DefaultComboBoxModel(moduleNames.toTypedArray())

        applyFilters()
    }

    private fun applyFilters() {
        val selectedModule = moduleSelector.selectedItem as? String ?: "ALL"
        val selectedSeverity = severitySelector.selectedItem as? String ?: "ALL"
        val selectedTarget = targetSelector.selectedItem as? String ?: "ALL"

        val relevantModules = modules.filter {
            selectedModule == "ALL" || it.moduleName == selectedModule
        }
        allViolations = relevantModules.flatMap { it.validationReport?.violations ?: emptyList() }

        filteredViolations = allViolations.filter { v ->
            val matchSeverity = selectedSeverity == "ALL" ||
                v.severity.equals(selectedSeverity, ignoreCase = true)
            val matchTarget = selectedTarget == "ALL" ||
                v.target.isNullOrBlank() ||
                v.target.equals(selectedTarget, ignoreCase = true)
            matchSeverity && matchTarget
        }

        val errors = allViolations.count { it.severity.equals("ERROR", ignoreCase = true) }
        val warnings = allViolations.count { it.severity.equals("WARNING", ignoreCase = true) }

        if (errors > 0) {
            statusSummaryLabel.text = "Store Compliance: FAILED"
            statusSummaryLabel.foreground = StoreStudioTheme.errorFg
        } else if (warnings > 0) {
            statusSummaryLabel.text = "Store Compliance: PASSED (with warnings)"
            statusSummaryLabel.foreground = StoreStudioTheme.warningFg
        } else {
            statusSummaryLabel.text = "Store Compliance: PASSED"
            statusSummaryLabel.foreground = StoreStudioTheme.validFg
        }

        countsSummaryLabel.text = "$errors Errors • $warnings Warnings • " +
            "${filteredViolations.size} Shown"

        tableModel.fireTableDataChanged()

        if (filteredViolations.isNotEmpty()) {
            table.setRowSelectionInterval(0, 0)
            updateDetails(filteredViolations[0])
        } else {
            clearDetails()
        }
    }

    private fun updateDetails(violation: ViolationDto) {
        detailRuleId.text = "${violation.severity}: ${violation.ruleId}"
        detailRuleId.foreground = when (violation.severity) {
            "ERROR" -> StoreStudioTheme.errorFg
            "WARNING" -> StoreStudioTheme.warningFg
            else -> StoreStudioTheme.textPrimary
        }
        // Messages are plain text; escape them so '<' or '&' cannot break the HTML label
        detailMessage.text =
            "<html><b>Finding:</b> ${StringUtil.escapeXmlEntities(violation.message)}</html>"
        val hintText = violation.hint ?: "Check composable annotations and export dimensions."
        detailHint.text =
            "<html><b>Remediation:</b> ${StringUtil.escapeXmlEntities(hintText)}</html>"
    }

    private fun clearDetails() {
        detailRuleId.text = "No findings"
        detailRuleId.foreground = StoreStudioTheme.textMuted
        detailMessage.text = "All store compliance checks passed successfully."
        detailHint.text = ""
    }

    private fun remediateViolation(violation: ViolationDto) {
        val slotKey = violation.slotKey ?: return
        // With "ALL" selected the violation may belong to any module, not just the first one
        val selected = moduleSelector.selectedItem as? String ?: "ALL"
        // Longest id wins so "phone_dark" is not shadowed by "phone"
        val entry = modules.filter { selected == "ALL" || it.moduleName == selected }
            .flatMap { it.manifest?.entries.orEmpty() }
            .filter { slotKey.contains(it.id) }
            .maxByOrNull { it.id.length }
        if (entry?.source != null) {
            StoreStudioNavigator.navigate(
                project = project,
                sourceLocation = entry.source,
                functionFqn = entry.functionFqn,
                functionName = entry.functionName,
                annotationId = entry.id,
            )
        }
    }

    private fun getActiveModule(): ModuleStoreArtifacts? {
        val selected = moduleSelector.selectedItem as? String ?: "ALL"
        return modules.find { selected == "ALL" || it.moduleName == selected }
            ?: modules.firstOrNull()
    }

    private inner class ViolationTableModel : AbstractTableModel() {
        private val columns = arrayOf("Severity", "Rule ID", "Target", "Slot / Device", "Message")

        override fun getRowCount(): Int = filteredViolations.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val v = filteredViolations[rowIndex]
            return when (columnIndex) {
                0 -> v.severity
                1 -> v.ruleId
                2 -> v.target ?: "ALL"
                3 -> v.slotKey ?: v.deviceSlot ?: "—"
                4 -> v.message
                else -> ""
            }
        }
    }
}
