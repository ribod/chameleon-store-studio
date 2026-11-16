package dev.ribod.storestudio.gradle.internal

import org.gradle.api.tasks.Internal
import org.gradle.process.CommandLineArgumentProvider

/**
 * Supplies `storeStudio.projectRootDir` to KSP without making the absolute path a task input.
 *
 * The processor only uses the root to relativize source paths, so generated output is identical
 * across machines and the KSP task can be served from a shared build cache.
 */
internal class StoreStudioRootDirArgumentProvider(@get:Internal val rootDir: String) :
    CommandLineArgumentProvider {
    override fun asArguments(): Iterable<String> = listOf("storeStudio.projectRootDir=$rootDir")
}
