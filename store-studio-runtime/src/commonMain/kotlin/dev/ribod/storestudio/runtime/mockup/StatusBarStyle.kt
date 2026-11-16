package dev.ribod.storestudio.runtime.mockup

import androidx.compose.runtime.Immutable

/**
 * Styling and configuration options for the mock status bar rendered above the app screen.
 *
 * All icons (cellular reception, Wi-Fi waves, battery indicator) are drawn natively with vector
 * primitives to ensure deterministic, pixel-identical output across all platform engines.
 * A `null` value hides the matching element; hidden elements take no space.
 */
@Immutable
public sealed class StatusBarStyle {
    /** Mock display time text (e.g. "9:41"); `null` hides it. */
    public abstract val time: String?

    /** Wi-Fi level 0..4; `null` hides the icon. */
    public abstract val wifi: Int?

    /** Cellular level 0..4; `null` hides the icon. */
    public abstract val cellular: Int?

    /** Battery percentage 0..100; `null` hides the icon. */
    public abstract val battery: Int?

    /** Shows a charging bolt in the battery icon. */
    public abstract val charging: Boolean

    /** When true, status bar text and icons are rendered with dark ink; otherwise white. */
    public abstract val darkIcons: Boolean

    /**
     * Automatically adapts status bar styling to match the surrounding [DeviceFrame] architecture.
     */
    public data class Auto(
        override val time: String? = DEFAULT_TIME,
        override val wifi: Int? = 4,
        override val cellular: Int? = 4,
        override val battery: Int? = 100,
        override val charging: Boolean = false,
        override val darkIcons: Boolean = false,
    ) : StatusBarStyle() {
        init {
            validate(wifi, cellular, battery)
        }
    }

    /**
     * Modern Android-style status bar with left-aligned time and right-aligned system glyphs.
     */
    public data class Android(
        override val time: String? = DEFAULT_TIME,
        override val wifi: Int? = 4,
        override val cellular: Int? = 4,
        override val battery: Int? = 100,
        override val charging: Boolean = false,
        override val darkIcons: Boolean = false,
    ) : StatusBarStyle() {
        init {
            validate(wifi, cellular, battery)
        }
    }

    /**
     * Modern iOS-style status bar split evenly across the left and right sides of the cutout.
     */
    public data class Ios(
        override val time: String? = DEFAULT_TIME,
        override val wifi: Int? = 4,
        override val cellular: Int? = 4,
        override val battery: Int? = 100,
        override val charging: Boolean = false,
        override val darkIcons: Boolean = false,
    ) : StatusBarStyle() {
        init {
            validate(wifi, cellular, battery)
        }
    }

    /**
     * Completely disables status bar rendering, giving the app screen full unobstructed display.
     */
    public data object None : StatusBarStyle() {
        override val time: String? = null
        override val wifi: Int? = null
        override val cellular: Int? = null
        override val battery: Int? = null
        override val charging: Boolean = false
        override val darkIcons: Boolean = false
    }

    public companion object {
        /** Standard default time displayed in marketing screenshots across both major stores. */
        public const val DEFAULT_TIME: String = "9:41"

        private fun validate(wifi: Int?, cellular: Int?, battery: Int?) {
            require(wifi == null || wifi in 0..4) { "wifi must be in 0..4 but was $wifi" }
            require(cellular == null || cellular in 0..4) {
                "cellular must be in 0..4 but was $cellular"
            }
            require(battery == null || battery in 0..100) {
                "battery must be in 0..100 but was $battery"
            }
        }
    }
}
