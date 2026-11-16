package dev.ribod.storestudio.idea.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Persisted values; property names match the previous bean so existing settings files load.
 */
public class StoreStudioState : BaseState() {
    public var autoReloadOnBuild: Boolean by property(true)
    public var showDeviceBadges: Boolean by property(true)
    public var showEngineBadges: Boolean by property(true)
    public var showValidationBadges: Boolean by property(true)
    public var defaultTargetFilter: String? by string("ALL")
    public var diffBaseRef: String? by string("")
}

/**
 * Project-level persistent settings for Chameleon Store Studio.
 */
@State(
    name = "StoreStudioSettings",
    storages = [Storage("compose-store-studio.xml")],
)
@Service(Service.Level.PROJECT)
public class StoreStudioSettings :
    SimplePersistentStateComponent<StoreStudioState>(StoreStudioState()) {

    public var autoReloadOnBuild: Boolean
        get() = state.autoReloadOnBuild
        set(value) {
            state.autoReloadOnBuild = value
        }

    /** Shows the device slot (e.g. PHONE, IPAD_13) in gallery card subtitles. */
    public var showDeviceBadges: Boolean
        get() = state.showDeviceBadges
        set(value) {
            state.showDeviceBadges = value
        }

    /** Shows the LAYOUTLIB / ROBOLECTRIC badge on gallery cards. */
    public var showEngineBadges: Boolean
        get() = state.showEngineBadges
        set(value) {
            state.showEngineBadges = value
        }

    /** Shows the ERROR / WARNING badge on gallery cards with validation findings. */
    public var showValidationBadges: Boolean
        get() = state.showValidationBadges
        set(value) {
            state.showValidationBadges = value
        }

    /** Store filter preselected in the gallery (ALL, PLAY_STORE or APP_STORE). */
    public var defaultTargetFilter: String
        get() = state.defaultTargetFilter ?: "ALL"
        set(value) {
            state.defaultTargetFilter = value
        }

    /**
     * Git ref rendered as the base by "Compare with base branch"; empty uses the merge base with
     * origin/main (or main).
     */
    public var diffBaseRef: String
        get() = state.diffBaseRef.orEmpty()
        set(value) {
            // Whitespace would split into extra Gradle arguments; keep the previous value instead
            state.diffBaseRef =
                value.trim().takeIf { it.none(Char::isWhitespace) } ?: state.diffBaseRef
        }

    public companion object {
        public fun getInstance(project: Project): StoreStudioSettings = project.service()
    }
}
