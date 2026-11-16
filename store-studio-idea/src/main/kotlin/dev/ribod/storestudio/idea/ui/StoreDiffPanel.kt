package dev.ribod.storestudio.idea.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.gradle.StoreGradleTaskRunner
import dev.ribod.storestudio.idea.model.DiffChangeDto
import dev.ribod.storestudio.idea.model.DiffRefInfoDto
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.settings.StoreStudioSettings
import dev.ribod.storestudio.idea.settings.StoreStudioTheme
import org.jetbrains.annotations.VisibleForTesting
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * Interactive visual regression and change diff panel in Store Studio.
 *
 * Displays git-backed change lists, real-time Before/After split curtain, and semantic text drift.
 */
public class StoreDiffPanel(
    private val project: Project,
    private val onRefreshRequested: () -> Unit,
) : SimpleToolWindowPanel(true, true) {

    private var modules: List<ModuleStoreArtifacts> = emptyList()
    private val moduleSelector = ComboBox(arrayOf("ALL"))

    /** A change together with the module whose build produced it. */
    private class DiffRow(val module: ModuleStoreArtifacts, val change: DiffChangeDto)

    private val diffListModel = DefaultListModel<DiffRow>()
    private var showStoreInRows = false

    /** Module names of the listed changes, in list order. */
    @get:VisibleForTesting
    internal val listedChangeModules: List<String>
        get() = (0 until diffListModel.size()).map { diffListModel[it].module.moduleName }

    @get:VisibleForTesting
    internal val summaryText: String
        get() = summaryLabel.text

    /** `module/id` of the selected change. */
    @get:VisibleForTesting
    internal val selectedChangeKey: String?
        get() = diffList.selectedValue?.let(::rowKey)

    @VisibleForTesting
    internal fun selectChange(module: String, id: String) {
        val index = (0 until diffListModel.size()).firstOrNull {
            rowKey(diffListModel[it]) == "$module/$id"
        } ?: return
        diffList.selectedIndex = index
    }

    /** Set while the module combo is repopulated, so it does not trigger a second refresh. */
    private var repopulating = false
    private val diffList = JBList(diffListModel)
    private val sliderCanvas = DiffSliderCanvas()

    private val summaryLabel = JBLabel("No diff data available")
    private val detailsContainer = JPanel(BorderLayout())
    private val semanticTextPanel = JPanel()
    private val overflowWarningLabel = JBLabel()

    init {
        val splitter = JBSplitter(false, 0.35f).apply {
            firstComponent = buildLeftPane()
            secondComponent = buildRightPane()
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
        }

        moduleSelector.addActionListener {
            if (!repopulating) updateSelectedModule()
        }

        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction(
                "Run storeDiff",
                "Calculate visual diff against baseline",
                AllIcons.Actions.Diff,
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    val activeModule = getActiveModule()
                    val modulePath =
                        activeModule?.moduleDir?.absolutePath ?: project.basePath ?: return
                    StoreGradleTaskRunner.runTask(project, modulePath, "storeDiff")
                }
            })
            add(object : AnAction(
                "Compare with Base Branch",
                "Render the base branch on this machine and compare (storeDiff --render-base)",
                AllIcons.Vcs.Branch,
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    val activeModule = getActiveModule()
                    val modulePath =
                        activeModule?.moduleDir?.absolutePath ?: project.basePath ?: return
                    val baseRef = StoreStudioSettings.getInstance(project).diffBaseRef
                    val options = buildList {
                        add("--render-base")
                        if (baseRef.isNotEmpty()) add("--against=$baseRef")
                    }
                    StoreGradleTaskRunner.runTask(project, modulePath, "storeDiff", options)
                }
            })
            add(object : AnAction("Refresh", "Reload diff outputs", AllIcons.Actions.Refresh) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
                override fun actionPerformed(e: AnActionEvent) {
                    onRefreshRequested()
                }
            })
        }

        val toolbar = ActionManager.getInstance().createActionToolbar(
            "StoreStudioDiff",
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
    }

    private fun buildLeftPane(): JPanel {
        val pane = JPanel(BorderLayout())
        pane.border = JBUI.Borders.customLine(StoreStudioTheme.cardBorder, 0, 0, 0, 1)

        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(10, 12)
            isOpaque = false
            add(summaryLabel, BorderLayout.CENTER)
        }
        pane.add(header, BorderLayout.NORTH)

        diffList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        diffList.cellRenderer = object : ColoredListCellRenderer<DiffRow>() {
            override fun customizeCellRenderer(
                list: JList<out DiffRow>,
                row: DiffRow?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean,
            ) {
                if (row == null) return
                val value = row.change
                val type = value.type
                val (color, prefix) = when (type) {
                    "ADDED" -> Pair(StoreStudioTheme.validFg, "[+]")
                    "REMOVED" -> Pair(StoreStudioTheme.errorFg, "[-]")
                    "MODIFIED", "MOVED_AND_MODIFIED" -> Pair(StoreStudioTheme.warningFg, "[~]")
                    "REORDERED" -> Pair(StoreStudioTheme.badgeAppStoreFg, "[⇄]")
                    else -> Pair(StoreStudioTheme.badgeLayoutlibFg, "[=]")
                }

                append(
                    "$prefix $type ",
                    SimpleTextAttributes(SimpleTextAttributes.STYLE_BOLD, color),
                )
                append(value.id, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                diffRowLabel(value.bucket, showStoreInRows)?.let {
                    append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }

                if (value.pHashDistance != null && value.pHashDistance > 0) {
                    append(
                        " (Δ ${(value.pHashDistance * 100).toInt()}%)",
                        SimpleTextAttributes.GRAYED_ATTRIBUTES,
                    )
                }

                if (value.hasVisualOverflow) {
                    append(" ⚠️", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                }

                if (modules.size > 1) {
                    append("  ${row.module.moduleName}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }

        diffList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateSelectedDiff(diffList.selectedValue)
            }
        }

        pane.add(
            JBScrollPane(diffList).apply {
                border = JBUI.Borders.empty()
            },
            BorderLayout.CENTER,
        )
        return pane
    }

    private fun buildRightPane(): JPanel {
        val pane = JPanel(BorderLayout())
        pane.add(sliderCanvas, BorderLayout.CENTER)

        val bottomPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            border = JBUI.Borders.empty(12, 16, 16, 16)
        }

        semanticTextPanel.layout = BoxLayout(semanticTextPanel, BoxLayout.Y_AXIS)
        semanticTextPanel.isOpaque = false
        bottomPanel.add(semanticTextPanel)

        overflowWarningLabel.apply {
            font = StoreStudioTheme.subtitleFont
            foreground = StoreStudioTheme.warningFg
            border = JBUI.Borders.empty(8, 0, 8, 0)
            alignmentX = Component.LEFT_ALIGNMENT
        }
        bottomPanel.add(overflowWarningLabel)

        pane.add(bottomPanel, BorderLayout.SOUTH)
        return pane
    }

    public fun updateArtifacts(modules: List<ModuleStoreArtifacts>) {
        this.modules = modules
        val moduleNames = listOf("ALL") + modules.map { it.moduleName }.distinct()
        // Reloads happen on every Gradle write; keep what the user is looking at
        repopulating = true
        try {
            moduleSelector.replaceOptionsKeepingSelection(moduleNames)
        } finally {
            repopulating = false
        }

        updateSelectedModule()
    }

    private fun updateSelectedModule() {
        // "ALL" combines the diff documents of every module
        val rows = selectedModules().flatMap { module ->
            module.diffDocument?.changes.orEmpty().map { DiffRow(module, it) }
        }

        val previousKey = diffList.selectedValue?.let(::rowKey)
        diffListModel.clear()
        if (rows.isEmpty()) {
            summaryLabel.text = "No visual diff document found in build/store/diff/"
            sliderCanvas.setImages(null, null)
            semanticTextPanel.removeAll()
            overflowWarningLabel.text = ""
            return
        }

        val summaries = selectedModules().mapNotNull { it.diffDocument?.summary }
        val bases = selectedModules().mapNotNull { it.diffDocument?.base }.distinct()
        val baseSuffix = bases.singleOrNull()?.let { " • vs ${describeBase(it)}" }.orEmpty()
        summaryLabel.text = "${summaries.sumOf { it.modified }} modified • " +
            "${summaries.sumOf { it.added }} added • ${summaries.sumOf { it.removed }} removed" +
            baseSuffix

        rows.forEach(diffListModel::addElement)
        val targets = rows.map { it.change.bucket.substringBefore('/') }.toSet()
        showStoreInRows = "PLAY_STORE" in targets && "APP_STORE" in targets
        diffList.selectedIndex = rows.indexOfFirst { rowKey(it) == previousKey }.coerceAtLeast(0)
    }

    private fun describeBase(base: DiffRefInfoDto): String = when (base.source) {
        "RENDERED_REF" -> "${base.ref} (rendered locally)"
        "BASE_EXPORT" -> "base export ${base.ref}"
        "GIT_BASELINE" -> "baseline at ${base.ref}"
        "LOCAL_DISK" -> "local baseline"
        else -> "no baseline"
    }

    private fun rowKey(row: DiffRow): String = "${row.module.moduleName}/${row.change.id}"

    private fun updateSelectedDiff(row: DiffRow?) {
        semanticTextPanel.removeAll()
        overflowWarningLabel.text = ""

        if (row == null) {
            sliderCanvas.setImages(null, null)
            semanticTextPanel.revalidate()
            semanticTextPanel.repaint()
            return
        }

        val change = row.change
        val storeDir = row.module.buildStoreDir
        // Image lookup probes several paths on disk; keep it off the EDT
        sliderCanvas.setImages(null, null)
        ApplicationManager.getApplication().executeOnPooledThread {
            val baseFile = resolveBaseImage(change, File(storeDir, "diff/base-images"))
            val headFile = change.headImage
                ?.let { File(File(storeDir, "export"), it).takeIf { f -> f.isFile } }
                ?: change.headImage?.let { File(it).takeIf { f -> f.isFile } }
            ApplicationManager.getApplication().invokeLater(
                {
                    // The user may have moved on to another change meanwhile
                    if (diffList.selectedValue === row) sliderCanvas.setImages(baseFile, headFile)
                },
                ModalityState.stateForComponent(this),
                project.disposed,
            )
        }

        // Semantic Text Drift
        if (change.removedTexts.isNotEmpty() || change.addedTexts.isNotEmpty()) {
            semanticTextPanel.add(
                JBLabel("Semantic Text Changes:").apply {
                    font = StoreStudioTheme.titleFont
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )
            for (removed in change.removedTexts) {
                semanticTextPanel.add(
                    JBLabel("- $removed").apply {
                        foreground = StoreStudioTheme.errorFg
                        font = StoreStudioTheme.subtitleFont
                        alignmentX = Component.LEFT_ALIGNMENT
                    },
                )
            }
            for (added in change.addedTexts) {
                semanticTextPanel.add(
                    JBLabel("+ $added").apply {
                        foreground = StoreStudioTheme.validFg
                        font = StoreStudioTheme.subtitleFont
                        alignmentX = Component.LEFT_ALIGNMENT
                    },
                )
            }
        }

        // Visual Text Overflow Warning
        if (change.hasVisualOverflow) {
            overflowWarningLabel.text =
                "⚠️ Visual Text Overflow: One or more Text elements in this slot " +
                "exceeded layout bounds and were clipped or truncated."
        }

        semanticTextPanel.revalidate()
        semanticTextPanel.repaint()
    }

    /**
     * Resolves the baseline image like the HTML diff report: absolute path written by storeDiff,
     * then the file name, sanitized slot key or pixel hash inside `diff/base-images`.
     */
    private fun resolveBaseImage(change: DiffChangeDto, baseImagesDir: File): File? {
        val base = change.baseImage ?: return null
        if (base == "AVAILABLE" || base == "UNAVAILABLE") return null
        // Absolute paths from diff.json must stay inside the module's build/store directory
        val storeDir = baseImagesDir.parentFile?.parentFile
        File(base).takeIf {
            it.isAbsolute && it.isFile && storeDir != null &&
                it.canonicalPath.startsWith(storeDir.canonicalPath + File.separator)
        }?.let { return it }
        return listOfNotNull(
            File(baseImagesDir, base),
            File(baseImagesDir, "${change.bucket}/${change.id}".replace('/', '_') + ".png"),
            change.basePixelSha256?.let { File(baseImagesDir, "$it.png") },
        ).firstOrNull { it.isFile }
    }

    private fun selectedModules(): List<ModuleStoreArtifacts> {
        val selected = moduleSelector.selectedItem as? String ?: "ALL"
        return if (selected == "ALL") modules else modules.filter { it.moduleName == selected }
    }

    private fun getActiveModule(): ModuleStoreArtifacts? {
        val selected = moduleSelector.selectedItem as? String ?: "ALL"
        return modules.find { selected == "ALL" || it.moduleName == selected }
            ?: modules.firstOrNull()
    }
}

/** Formats "PLAY_STORE/PHONE/en-US" as "PHONE • en-US", optionally prefixed with the store. */
@VisibleForTesting
internal fun diffRowLabel(bucket: String?, showStore: Boolean): String? {
    if (bucket.isNullOrBlank()) return null
    val parts = bucket.split('/')
    if (parts.size != 3) return bucket
    val store = if (!showStore) {
        ""
    } else if (parts[0] == "APP_STORE") {
        "App Store • "
    } else {
        "Play • "
    }
    return "$store${parts[1]} • ${parts[2]}"
}
