package dev.ribod.storestudio.idea.settings

import com.intellij.util.messages.Topic

/**
 * Listener invoked whenever Chameleon Store Studio settings are modified and applied.
 */
public fun interface StoreStudioSettingsListener {

    public fun settingsChanged(settings: StoreStudioSettings)

    public companion object {
        public val TOPIC: Topic<StoreStudioSettingsListener> = Topic.create(
            "Store Studio Settings Changed",
            StoreStudioSettingsListener::class.java,
        )
    }
}
