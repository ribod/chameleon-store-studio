package dev.ribod.storestudio.idea.publish

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import dev.ribod.storestudio.idea.gradle.StoreGradleTaskRunner
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts

/**
 * Safety-first dialog for triggering store publishing tasks to Google Play and App Store Connect.
 */
public class StorePublishDialog internal constructor(
    private val project: Project,
    private val modules: List<ModuleStoreArtifacts>,
    private val launchPublish: (modulePath: String, taskOptions: List<String>) -> Unit,
) : DialogWrapper(project) {

    public constructor(project: Project, modules: List<ModuleStoreArtifacts>) : this(
        project,
        modules,
        { modulePath, taskOptions ->
            StoreGradleTaskRunner.runTask(project, modulePath, "storePublish", taskOptions)
        },
    )

    private var selectedModule: String = modules.firstOrNull()?.moduleName ?: ""
    private var selectedTarget: String = TARGET_ALL
    private var selectedTrack: String = "internal" // internal, alpha, beta, production
    private var isDryRun: Boolean = true
    private var isConfirmed: Boolean = false

    init {
        title = "Publish Store Assets"
        setOKButtonText("Publish")
        init()
        updateValidation()
    }

    /** The form built by [createCenterPanel]; exposed so tests can drive it headlessly. */
    internal lateinit var form: DialogPanel
        private set

    // Returned as the DialogPanel itself: DialogWrapper only applies the bindings below on OK when
    // createCenterPanel() returns the DialogPanel, not a container around it.
    override fun createCenterPanel(): DialogPanel = buildForm().also { form = it }

    private fun buildForm(): DialogPanel = panel {
        row {
            label(
                "Deploy marketing screenshots and localized listing " +
                    "metadata directly to store consoles.",
            )
                .applyToComponent { font = JBFont.label() }
        }

        group("Deployment Target") {
            if (modules.size > 1) {
                row("Module:") {
                    comboBox(modules.map { it.moduleName })
                        .bindItem(
                            getter = { selectedModule },
                            setter = { selectedModule = it ?: "" },
                        )
                }
            }

            row("Store Marketplace:") {
                comboBox(listOf(TARGET_ALL, "PLAY_STORE", "APP_STORE"))
                    .bindItem(
                        getter = { selectedTarget },
                        setter = { selectedTarget = it ?: TARGET_ALL },
                    )
                    .applyToComponent { prototypeDisplayValue = TARGET_ALL }
            }

            row("Release Track:") {
                comboBox(listOf("internal", "alpha", "beta", "production"))
                    .bindItem(
                        getter = { selectedTrack },
                        setter = { selectedTrack = it ?: "internal" },
                    )
            }
        }

        group("Safety && Verification") {
            row {
                checkBox("Dry-run only (verify credentials and payload without publishing)")
                    .bindSelected(::isDryRun)
                    // Bindings apply on OK; the confirmation gate must follow the checkbox live
                    .onChanged {
                        isDryRun = it.isSelected
                        updateValidation()
                    }
            }

            row {
                checkBox("I confirm that assets and localized metadata are verified for release.")
                    .bindSelected(::isConfirmed)
                    .onChanged {
                        isConfirmed = it.isSelected
                        updateValidation()
                    }
            }
        }
    }.withBorder(JBUI.Borders.empty(8))

    private fun updateValidation() {
        // Require explicit confirmation if not dry-run
        isOKActionEnabled = isDryRun || isConfirmed
    }

    override fun doOKAction() {
        // Applies the bound selections before they are read
        super.doOKAction()
        if (!isOK) return

        val targetModule = modules.find { it.moduleName == selectedModule } ?: modules.firstOrNull()
        val modulePath = targetModule?.moduleDir?.absolutePath ?: project.basePath ?: return

        launchPublish(modulePath, buildPublishTaskOptions(selectedTarget, selectedTrack, isDryRun))
    }

    private companion object {
        const val TARGET_ALL = "ALL (Google Play + App Store)"
    }
}

/**
 * Task options understood by `storePublish`. `--publish-dry-run` is used instead of `--dry-run`,
 * which collides with Gradle's built-in flag that skips every task action.
 */
internal fun buildPublishTaskOptions(
    selectedTarget: String,
    selectedTrack: String,
    isDryRun: Boolean,
): List<String> = buildList {
    when {
        selectedTarget.startsWith("PLAY") -> add("--target=PLAY_STORE")
        selectedTarget.startsWith("APP") -> add("--target=APP_STORE")
    }
    // Release notes from store metadata are applied to this Google Play track
    add("--track=$selectedTrack")
    if (isDryRun) add("--publish-dry-run")
}
