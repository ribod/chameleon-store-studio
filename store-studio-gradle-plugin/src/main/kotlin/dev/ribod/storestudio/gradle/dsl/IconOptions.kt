package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.file.RegularFileProperty

/**
 * Location of the app icon of each store. When a store's icon is not set, Store Studio looks for the
 * conventional file of that platform (`ic_launcher-playstore.png` or `mipmap-<density>/ic_launcher` for
 * Google Play, `AppIcon.appiconset` for the App Store). If the platform has none, the other store's
 * icon is used for previews, and nothing is shown if neither exists. Only the configured or Android
 * icon is ever uploaded to Google Play.
 */
public abstract class IconOptions {
    /** Icon shown for, and uploaded to, Google Play. */
    public abstract val googlePlay: RegularFileProperty

    /** Icon shown for the App Store. */
    public abstract val appStore: RegularFileProperty
}
