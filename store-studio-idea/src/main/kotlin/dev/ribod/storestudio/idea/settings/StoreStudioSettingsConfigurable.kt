package dev.ribod.storestudio.idea.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

/**
 * Settings page for Chameleon Store Studio under **Settings → Tools → Chameleon Store Studio**.
 *
 * Implemented using IntelliJ's Kotlin UI DSL v2 for optimal stability and appearance.
 */
public class StoreStudioSettingsConfigurable(private val project: Project) :
    BoundConfigurable("Chameleon Store Studio") {

    private val settings: StoreStudioSettings = StoreStudioSettings.getInstance(project)

    override fun createPanel(): DialogPanel = panel {
        group("Synchronization && Detection") {
            row {
                checkBox("Automatically reload tool window on Gradle build completion")
                    .bindSelected(settings::autoReloadOnBuild)
                    .comment(
                        "Monitors build/store output directory and refreshes preview canvases live",
                    )
            }
        }

        group("Gallery && Card Overlays") {
            row {
                checkBox("Show device slot badges (e.g. PHONE, TABLET_7, IPAD_13)")
                    .bindSelected(settings::showDeviceBadges)
            }
            row {
                checkBox("Show rendering engine badges (LAYOUTLIB / ROBOLECTRIC)")
                    .bindSelected(settings::showEngineBadges)
            }
            row {
                checkBox("Show compliance validation status indicators")
                    .bindSelected(settings::showValidationBadges)
            }
        }

        group("Changes && Diff") {
            row("Base branch:") {
                textField()
                    .bindText(settings::diffBaseRef)
                    .comment(
                        "Rendered locally by \"Compare with base branch\". Leave empty to use " +
                            "the merge base with origin/main or main.",
                        maxLineLength = MAX_LINE_LENGTH_WORD_WRAP,
                    )
            }
        }

        group("Default Filter Preferences") {
            row("Default Store Marketplace:") {
                comboBox(listOf("ALL", "PLAY_STORE", "APP_STORE"))
                    .bindItem(
                        getter = { settings.defaultTargetFilter },
                        setter = { settings.defaultTargetFilter = it ?: "ALL" },
                    )
            }
        }
    }

    override fun apply() {
        super.apply()
        project.messageBus.syncPublisher(
            StoreStudioSettingsListener.TOPIC,
        ).settingsChanged(settings)
    }
}
