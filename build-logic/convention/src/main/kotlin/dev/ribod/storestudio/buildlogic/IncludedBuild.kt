package dev.ribod.storestudio.buildlogic

import org.gradle.api.Project

/**
 * Whether this repository is being built as an included build, e.g. by the samples through
 * `includeBuild("../../")`. Tooling that only matters for developing the repository itself is
 * skipped then, so the consuming build configures and loads less.
 */
public val Project.isIncludedBuild: Boolean
    get() = gradle.parent != null

/**
 * iOS targets are only registered when building the repository itself, or when an including build
 * opts in with `-PstoreStudio.includeIos=true`; the samples target Android and the JVM only.
 */
public val Project.iosTargetsEnabled: Boolean
    get() = !isIncludedBuild || providers.gradleProperty("storeStudio.includeIos").isPresent
