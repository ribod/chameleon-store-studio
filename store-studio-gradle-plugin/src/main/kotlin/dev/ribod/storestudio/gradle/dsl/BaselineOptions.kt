package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options governing baseline tracking and visual regression drift detection.
 */
public abstract class BaselineOptions @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout,
) {
    /** Location of the tracked baseline hashes JSON document. */
    public val file: RegularFileProperty = objects.fileProperty()
        .convention(layout.projectDirectory.file(".store/baseline.json"))

    /**
     * Algorithm used to compare rendered assets against the baseline.
     *
     * Defaults to [BaselineComparison.STRICT]: renders are deterministic on the same machine, so
     * exact pixel hashes catch every change. Use [BaselineComparison.PERCEPTUAL] only when the
     * baseline is recorded on a different operating system than the one verifying it.
     */
    public val comparison: Property<BaselineComparison> =
        objects.property(BaselineComparison::class.java)
            .convention(BaselineComparison.STRICT)

    /** Normalized perceptual Hamming distance tolerance threshold (0.00 to 1.00). */
    public val perceptualThreshold: Property<Double> = objects.property(Double::class.java)
        .convention(0.05)

    /** Whether the verification task fails when unapproved visual drift is detected. */
    public val failOnDrift: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Export directory (with its `index.json`) rendered from the base revision, for example the
     * `build/store/export` of the default branch downloaded from a CI artifact. When set, `storeDiff`
     * compares against it instead of the committed baseline. Also settable with `--base-export`.
     */
    public val baseExportDir: DirectoryProperty = objects.directoryProperty()

    /**
     * Whether `storeVerify` fails when no baseline has been recorded. When `false` (default) the
     * verification is skipped with a notice, so projects that compare against a rendered base
     * revision don't need to commit a baseline.
     */
    public val required: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)
}
