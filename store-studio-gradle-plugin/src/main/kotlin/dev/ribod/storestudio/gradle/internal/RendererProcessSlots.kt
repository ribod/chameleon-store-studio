package dev.ribod.storestudio.gradle.internal

import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.util.concurrent.Semaphore

/**
 * Build-wide limit on concurrently running renderer JVMs.
 *
 * Every module's render task starts its own worker processes; with `org.gradle.parallel` a
 * multi-module build would otherwise run `modules x maxParallelProcesses` JVMs (2 GB heap each by
 * default) at once. All render tasks of a build share these slots.
 */
internal abstract class RendererProcessSlots : BuildService<RendererProcessSlots.Params> {

    internal interface Params : BuildServiceParameters {
        val maxProcesses: Property<Int>
    }

    private val slots = Semaphore(parameters.maxProcesses.get().coerceAtLeast(1), true)

    /** Runs [block] while holding one renderer process slot. */
    fun <T> withSlot(block: () -> T): T {
        slots.acquire()
        try {
            return block()
        } finally {
            slots.release()
        }
    }

    companion object {
        private const val NAME = "storeStudioRendererProcessSlots"

        /** The first registering project's `rendering.maxParallelProcesses` sets the limit. */
        fun register(
            project: Project,
            maxProcesses: Provider<Int>,
        ): Provider<RendererProcessSlots> = project.gradle.sharedServices.registerIfAbsent(
            NAME,
            RendererProcessSlots::class.java,
        ) { spec -> spec.parameters.maxProcesses.set(maxProcesses) }
    }
}
