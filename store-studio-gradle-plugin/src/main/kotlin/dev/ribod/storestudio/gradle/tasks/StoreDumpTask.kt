package dev.ribod.storestudio.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Inspection task printing the fully resolved matrix of targets, device slots, and locales.
 *
 * Invoked via `./gradlew storeDump`.
 */
public abstract class StoreDumpTask : DefaultTask() {

    @get:Input
    public abstract val variant: Property<String>

    @get:Input
    public abstract val targets: SetProperty<String>

    @get:Input
    public abstract val defaultDevices: SetProperty<String>

    @get:Input
    public abstract val locales: ListProperty<String>

    @get:Input
    public abstract val renderingEngine: Property<String>

    @get:Input
    public abstract val baselineComparison: Property<String>

    init {
        group = "store studio"
        description = "Prints the fully resolved Store Studio execution matrix and configurations."
    }

    @TaskAction
    public fun dumpMatrix() {
        val lines = mutableListOf<String>()
        lines.add("==================================================================")
        lines.add("             Chameleon Store Studio: Configuration Matrix           ")
        lines.add("==================================================================")
        lines.add("Active Build Variant: ${variant.get()}")
        lines.add("Target Marketplaces : ${targets.get().joinToString(", ")}")
        lines.add("Default Device Slots: ${defaultDevices.get().joinToString(", ")}")
        lines.add(
            "Target Locales      : ${locales.get().ifEmpty {
                listOf("Default (none registered)")
            }.joinToString(", ")}",
        )
        lines.add("Rendering Engine    : ${renderingEngine.get()}")
        lines.add("Baseline Comparison : ${baselineComparison.get()}")
        lines.add("==================================================================")

        for (line in lines) {
            println(line)
        }
    }
}
