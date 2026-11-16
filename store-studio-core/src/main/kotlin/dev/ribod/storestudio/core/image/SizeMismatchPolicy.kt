package dev.ribod.storestudio.core.image

/**
 * Strategy applied when rendered visual dimensions deviate slightly from catalog specifications.
 */
public enum class SizeMismatchPolicy {
    /**
     * Symmetrically crops extra pixels or pads missing borders with the background color
     * when dimensions are within the allowed pixel tolerance.
     */
    CROP_OR_PAD,

    /**
     * Immediately fails the export process on any non-zero dimension deviation.
     */
    FAIL,
}
