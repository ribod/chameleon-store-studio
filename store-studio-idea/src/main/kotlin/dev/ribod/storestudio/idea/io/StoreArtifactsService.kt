package dev.ribod.storestudio.idea.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.AppExecutorUtil
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.StoreAssetCard
import dev.ribod.storestudio.idea.settings.StoreStudioSettings
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Project-level owner of the discovered Store Studio build artifacts.
 *
 * Scans run one at a time on a dedicated background queue. Every request bumps a generation
 * counter, so a scan that has been superseded is skipped or discarded and results can never be
 * applied out of order. VFS-triggered requests are debounced, explicit requests run immediately.
 *
 * Implemented with platform executors rather than coroutines: suspend code compiled by newer
 * Kotlin compilers is not readable by the coroutine debug probes of older IDE stdlibs.
 */
@Service(Service.Level.PROJECT)
public class StoreArtifactsService(private val project: Project) : Disposable {

    /** Immutable result of one artifact scan; cards are precomputed off the EDT. */
    public class Snapshot(
        public val modules: List<ModuleStoreArtifacts>,
        public val cards: List<StoreAssetCard>,
    )

    private val scanQueue = AppExecutorUtil.createBoundedApplicationPoolExecutor(
        "Store Studio Artifact Scan",
        1,
    )
    private val generation = AtomicLong()
    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()
    private val debounceLock = Any()
    private var pendingFileReload: ScheduledFuture<*>? = null

    /** Latest published snapshot, or `null` until the first scan completes. */
    @Volatile
    public var latest: Snapshot? = null
        private set

    init {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (events.any(::affectsStoreOutputs) &&
                        StoreStudioSettings.getInstance(project).autoReloadOnBuild
                    ) {
                        requestDebouncedReload()
                    }
                }
            },
        )
    }

    /**
     * Registers [listener] to receive every new snapshot on the EDT until [parentDisposable] is
     * disposed. The latest snapshot, if any, is delivered right away.
     */
    public fun addListener(parentDisposable: Disposable, listener: (Snapshot) -> Unit) {
        listeners += listener
        Disposer.register(parentDisposable) { listeners -= listener }
        latest?.let { snapshot -> deliver(snapshot, listOf(listener)) }
    }

    /** Requests an immediate rescan of all modules. */
    public fun requestReload() {
        val requested = generation.incrementAndGet()
        scanQueue.execute { scan(requested) }
    }

    private fun requestDebouncedReload() {
        synchronized(debounceLock) {
            pendingFileReload?.cancel(false)
            pendingFileReload = AppExecutorUtil.getAppScheduledExecutorService().schedule(
                { if (!project.isDisposed) requestReload() },
                DEBOUNCE_DELAY_MS,
                TimeUnit.MILLISECONDS,
            )
        }
    }

    private fun scan(requested: Long) {
        // A newer request is already queued behind this one
        if (project.isDisposed || requested != generation.get()) return

        val snapshot = try {
            load()
        } catch (e: Exception) {
            thisLogger().warn("Store Studio: artifact scan failed", e)
            return
        }

        if (requested != generation.get()) return
        latest = snapshot
        deliver(snapshot, listeners)
    }

    private fun deliver(snapshot: Snapshot, targets: List<(Snapshot) -> Unit>) {
        ApplicationManager.getApplication().invokeLater(
            {
                // Drop stale deliveries if a newer snapshot was published meanwhile
                if (latest === snapshot) targets.forEach { it(snapshot) }
            },
            // Tool window content: not delivered into modal dialogs (they get it once closed)
            ModalityState.nonModal(),
            project.disposed,
        )
    }

    private fun load(): Snapshot {
        // Rebuilt assets keep their paths; drop thumbnails whose files changed on disk
        StoreThumbnailService.getInstance().invalidateStale()
        val modules = StoreArtifactReader.loadAll(project)

        // Load the store directories into the VFS so later Gradle writes produce change events
        // even when nobody has browsed build/ in the IDE.
        LocalFileSystem.getInstance().refreshIoFiles(
            modules.map { it.buildStoreDir },
            true,
            true,
            null,
        )
        return Snapshot(modules, StoreArtifactReader.computeAssetCards(modules))
    }

    /**
     * Cheap filter run on the EDT for every VFS batch: only store outputs under this project or
     * under a module directory found by the last scan (included builds may live elsewhere).
     */
    private fun affectsStoreOutputs(event: VFileEvent): Boolean {
        val path = event.path
        if (!path.contains("/build/store/") && !path.contains("/.store/")) return false
        val basePath = project.basePath
        // Ancestry, not string prefixes: '/work/app' must not match '/work/app-old'
        if (basePath != null && FileUtil.isAncestor(basePath, path, false)) return true
        return latest?.modules?.any { FileUtil.isAncestor(it.moduleDir.path, path, false) } == true
    }

    override fun dispose() {
        synchronized(debounceLock) { pendingFileReload?.cancel(false) }
        listeners.clear()
    }

    public companion object {
        private const val DEBOUNCE_DELAY_MS = 350L

        public fun getInstance(project: Project): StoreArtifactsService = project.service()
    }
}
