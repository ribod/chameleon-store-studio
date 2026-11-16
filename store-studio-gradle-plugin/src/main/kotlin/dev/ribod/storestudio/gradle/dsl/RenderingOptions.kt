package dev.ribod.storestudio.gradle.dsl

import dev.ribod.storestudio.gradle.StoreStudioBuildConfig
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.time.Duration
import javax.inject.Inject

/**
 * Configuration options governing the headless screenshot rendering engine.
 *
 * Controls process isolation, parallel process bounds, timeouts, and execution parameters.
 */
public abstract class RenderingOptions @Inject constructor(objects: ObjectFactory) {
    /** Primary headless rendering technology to execute. */
    public val engine: Property<RenderingEngine> = objects.property(RenderingEngine::class.java)
        .convention(RenderingEngine.LAYOUTLIB)

    /** Secondary fallback engine invoked if the primary engine encounters fatal failures. */
    public val fallbackEngine: Property<RenderingEngine> = objects.property(
        RenderingEngine::class.java,
    )
        .convention(RenderingEngine.ROBOLECTRIC)

    /** Layoutlib framework runtime version to unpack and execute. */
    public val layoutlibVersion: Property<String> = objects.property(String::class.java)
        .convention(StoreStudioBuildConfig.LAYOUTLIB_VERSION)

    /** Robolectric framework runtime version for fallback execution. */
    public val robolectricVersion: Property<String> = objects.property(String::class.java)
        .convention(StoreStudioBuildConfig.ROBOLECTRIC_VERSION)

    /** Internal runner version bundled with the plugin distribution. */
    public val rendererVersion: Property<String> = objects.property(String::class.java)
        .convention(StoreStudioBuildConfig.COMPOSE_PREVIEW_RENDERER_VERSION)

    /** Maximum number of external JVM subprocesses launched concurrently for batch rendering. */
    public val maxParallelProcesses: Property<Int> = objects.property(Int::class.java)
        .convention(2)

    /** Number of screenshot jobs assigned per batch process run. */
    public val batchSize: Property<Int> = objects.property(Int::class.java)
        .convention(40)

    /** Total execution timeout allocated to a complete rendering run. */
    public val timeout: Property<Duration> = objects.property(Duration::class.java)
        .convention(Duration.ofMinutes(10))

    /** Maximum time the Robolectric fallback worker process may run. */
    public val fallbackTimeout: Property<Duration> = objects.property(Duration::class.java)
        .convention(Duration.ofMinutes(5))

    /** Maximum allowed inactivity duration before considering a worker process stalled. */
    public val stallTimeout: Property<Duration> = objects.property(Duration::class.java)
        .convention(Duration.ofSeconds(60))

    /**
     * Lets Android and KMP library modules render their own screenshots.
     *
     * Library builds do not link Android resources, so the plugin enables AGP's
     * `includeAndroidResources` for the module's unit tests (KMP: host tests) and renders with
     * those linked resources. This also makes the module's own Robolectric unit tests use real
     * resources. Set to `false` to leave unit tests untouched; library screenshots are then
     * rendered by the application module that depends on the library.
     */
    public val configureLibraryResources: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /** Custom JVM arguments supplied to external renderer worker processes. */
    public val rendererJvmArgs: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("-Xmx2g"))

    /**
     * JVM arguments for the Robolectric fallback process. Bounded like [rendererJvmArgs]; without
     * a limit the JVM would reserve up to a quarter of the machine's memory.
     */
    public val fallbackJvmArgs: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(listOf("-Xmx2g"))
}
