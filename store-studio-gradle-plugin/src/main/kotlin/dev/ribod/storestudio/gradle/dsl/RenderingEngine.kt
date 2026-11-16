package dev.ribod.storestudio.gradle.dsl

/**
 * Headless rendering engine technologies supported by Store Studio.
 */
public enum class RenderingEngine {
    /**
     * High-fidelity headless rendering utilizing the official Android Layoutlib engine.
     *
     * Provides near-instantaneous composable rasterization without requiring an active Android
     * emulator or physical test hardware.
     */
    LAYOUTLIB,

    /**
     * Fallback headless execution utilizing Robolectric Native Graphics (RNG).
     *
     * Emulates hardware-accelerated Skia graphics to execute tests and composables with
     * broad compatibility across customized framework hooks.
     */
    ROBOLECTRIC,

    /**
     * Completely disables engine execution or secondary fallbacks.
     */
    NONE,
}
