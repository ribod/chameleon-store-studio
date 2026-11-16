package dev.ribod.storestudio.model

import dev.ribod.storestudio.annotations.DeviceFamily
import kotlinx.serialization.Serializable

/**
 * Complete, authoritative technical specification for a device slot across app stores.
 *
 * Governs rendering geometry, store API parameter names, format constraints, and pre-flight
 * compliance validation.
 *
 * @property slot Discrete device slot identifier.
 * @property storeApiId Remote store API identifier (e.g. Google Play `imageType` or ASC `screenshotDisplayType`).
 * @property storeApiIdVerified Whether the remote store API parameter has been verified with live API calls.
 * @property families Device families that map to this slot (empty for promotional graphics).
 * @property defaultGeometry Default rendering geometry (exact size and dpi).
 * @property sizeRule Dimension validation rule enforced during export.
 * @property count Permissible asset counts per locale.
 * @property formats Image encoding formats accepted by the store.
 * @property alphaAllowed Whether transparent alpha channels are allowed (false for all major store slots).
 * @property colorSpace Required color space profile (e.g. `"sRGB"`).
 * @property maxBytes Strict maximum file size in bytes (causes validation error).
 * @property softMaxBytes Advisory maximum file size in bytes (causes validation warning).
 * @property frame Hardware bezel frame rendered around screenshots in the default canvas.
 * @property optional Whether this slot is optional (e.g. legacy iPhone 6.5").
 * @property sources Documentation references from Google Play and Apple Developer.
 */
@Serializable
public data class DeviceSlotSpec(
    public val slot: StoreDeviceSlot,
    public val storeApiId: String,
    public val storeApiIdVerified: Boolean,
    public val families: Set<DeviceFamily>,
    public val defaultGeometry: RenderGeometry,
    public val sizeRule: SizeRule,
    public val count: CountRule,
    public val formats: Set<ImageFormat>,
    public val alphaAllowed: Boolean,
    public val colorSpace: String,
    public val maxBytes: Long?,
    public val softMaxBytes: Long?,
    public val frame: FrameTypeName,
    public val optional: Boolean,
    public val sources: List<SourceRef> = emptyList(),
)
