package dev.ribod.storestudio.runtime.mockup

internal actual fun platformStatusBarDefaults(): StatusBarStyle =
    parseStatusBarSpec(System.getProperty("storestudio.statusBar"))
