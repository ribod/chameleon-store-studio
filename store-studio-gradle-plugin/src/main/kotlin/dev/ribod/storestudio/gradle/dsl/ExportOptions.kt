package dev.ribod.storestudio.gradle.dsl

import dev.ribod.storestudio.core.image.SizeMismatchPolicy
import dev.ribod.storestudio.model.ExportLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * Configuration options governing the asset export, alpha flattening, and encoding pipeline.
 */
public abstract class ExportOptions @Inject constructor(objects: ObjectFactory) {

    /**
     * Hexadecimal background color (`#RRGGBB`) used when flattening transparent rasters.
     *
     * Alpha channels are strictly prohibited by store submission policies.
     */
    public val background: Property<String> = objects.property(String::class.java)
        .convention("#FFFFFF")

    /**
     * Permitted dimension deviation in physical pixels before export adjustment or failure.
     *
     * Permissible values: `0..2`. Deviations greater than 2 are considered configuration errors.
     */
    public val sizeTolerancePx: Property<Int> = objects.property(Int::class.javaObjectType)
        .convention(2)

    /**
     * Policy applied when rendered dimensions deviate within [sizeTolerancePx].
     */
    public val sizeMismatchPolicy: Property<SizeMismatchPolicy> =
        objects.property(SizeMismatchPolicy::class.java)
            .convention(SizeMismatchPolicy.CROP_OR_PAD)

    /**
     * Whether to automatically re-encode assets as JPEG when PNG files exceed store size limits.
     */
    public val jpegFallback: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    /**
     * Initial compression quality applied during JPEG fallback encoding ladder.
     */
    public val jpegQuality: Property<Float> = objects.property(Float::class.javaObjectType)
        .convention(0.92f)

    /**
     * Directory nesting strategy for exported assets.
     *
     * Defaults to [ExportLayout.STORE_LOCALE_DEVICE] (`<store>/<locale>/<device>/...`).
     */
    public val layout: Property<ExportLayout> = objects.property(ExportLayout::class.java)
        .convention(ExportLayout.STORE_LOCALE_DEVICE)

    /**
     * Whether store and device folder names are lowercased (`play_store`, `phone`).
     */
    public val lowercaseFolders: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)
}
