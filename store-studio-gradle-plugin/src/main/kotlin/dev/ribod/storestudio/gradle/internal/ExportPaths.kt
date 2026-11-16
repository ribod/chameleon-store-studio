package dev.ribod.storestudio.gradle.internal

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.ExportLayout

/**
 * Folder naming shared by every task that writes into the store export, so images and texts
 * always land in matching locations.
 */
internal object ExportPaths {

    /** Folder name of a store target (e.g. `play_store`, or `PLAY_STORE` when not lowercased). */
    fun storeFolder(target: StoreTarget, lowercase: Boolean): String =
        if (lowercase) target.name.lowercase() else target.name

    /**
     * Folder, relative to the export root, that holds the device folders of one store and locale
     * for [layout] (e.g. `play_store/es-MX`, `es-MX/play_store`, or just `es-MX`).
     */
    fun localeFolder(
        layout: ExportLayout,
        lowercase: Boolean,
        target: StoreTarget,
        locale: String,
    ): String {
        val store = storeFolder(target, lowercase)
        return when (layout) {
            ExportLayout.STORE_LOCALE_DEVICE -> "$store/$locale"
            ExportLayout.LOCALE_STORE_DEVICE -> "$locale/$store"
            ExportLayout.LOCALE_DEVICE -> locale
        }
    }
}
