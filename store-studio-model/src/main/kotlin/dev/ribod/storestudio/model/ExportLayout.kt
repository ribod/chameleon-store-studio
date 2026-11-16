package dev.ribod.storestudio.model

/**
 * Directory nesting strategy applied to exported store assets.
 */
public enum class ExportLayout {
    /**
     * Groups assets by store target, then locale, then device slot:
     * `<target>/<locale>/<device_slot>/<order>_<id>.<ext>`
     * (e.g. `play_store/es-MX/phone/01_foryou_feed.png`).
     *
     * Recommended layout for Google Play Console and App Store Connect pipelines.
     */
    STORE_LOCALE_DEVICE,

    /**
     * Groups assets by locale, then store target, then device slot:
     * `<locale>/<target>/<device_slot>/<order>_<id>.<ext>`
     * (e.g. `es-MX/play_store/phone/01_foryou_feed.png`).
     */
    LOCALE_STORE_DEVICE,

    /**
     * Groups assets by locale, then device slot directly:
     * `<locale>/<device_slot>/<order>_<id>.<ext>`
     * (e.g. `es-MX/phone/01_foryou_feed.png`).
     */
    LOCALE_DEVICE,
}
