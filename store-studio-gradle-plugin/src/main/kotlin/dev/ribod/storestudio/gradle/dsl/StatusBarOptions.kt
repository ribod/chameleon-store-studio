package dev.ribod.storestudio.gradle.dsl

import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/** Look of the fake device status bar drawn by `DeviceMockup`. */
public enum class StatusBarStyleKind { AUTO, ANDROID, IOS }

/**
 * Global fake status bar for screenshots (`storeStudio { statusBar { ... } }`). Off by default;
 * individual screenshots can override it with `DeviceMockup(statusBar = ...)`.
 */
public abstract class StatusBarOptions @Inject constructor(objects: ObjectFactory) {
    /** Draws a status bar on every device mockup unless a screenshot overrides it. */
    public val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(false)

    /** AUTO picks the iOS or Android look from the device frame. */
    public val style: Property<StatusBarStyleKind> =
        objects.property(StatusBarStyleKind::class.java).convention(StatusBarStyleKind.AUTO)

    /** Clock text. */
    public val time: Property<String> = objects.property(String::class.java).convention("9:41")

    /** Wi-Fi level 0..4. */
    public val wifi: Property<Int> = objects.property(Int::class.java).convention(4)

    /** Cellular level 0..4. */
    public val cellular: Property<Int> = objects.property(Int::class.java).convention(4)

    /** Battery percentage 0..100. */
    public val battery: Property<Int> = objects.property(Int::class.java).convention(100)

    /** Shows a charging bolt in the battery. */
    public val charging: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    /** Dark ink instead of white. */
    public val darkIcons: Property<Boolean> =
        objects.property(Boolean::class.java).convention(false)

    public val showTime: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    public val showWifi: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    public val showCellular: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)
    public val showBattery: Property<Boolean> =
        objects.property(Boolean::class.java).convention(true)

    /**
     * Value of `-Dstorestudio.statusBar` (`key=value;...`, parsed by the runtime), or null when
     * disabled. Hidden elements are omitted. Throws [GradleException] on out-of-range values.
     */
    internal fun toSystemProperty(): String? {
        if (!enabled.get()) return null
        fun level(name: String, value: Int, max: Int): Int {
            if (value !in 0..max) {
                throw GradleException(
                    "storeStudio.statusBar.$name must be in 0..$max but was $value",
                )
            }
            return value
        }
        return buildList {
            add("style=${style.get().name.lowercase()}")
            if (showTime.get()) add("time=${time.get().replace("%", "%25").replace(";", "%3B")}")
            if (showWifi.get()) add("wifi=${level("wifi", wifi.get(), 4)}")
            if (showCellular.get()) add("cellular=${level("cellular", cellular.get(), 4)}")
            if (showBattery.get()) add("battery=${level("battery", battery.get(), 100)}")
            add("charging=${charging.get()}")
            add("dark=${darkIcons.get()}")
        }.joinToString(";")
    }
}
