package dev.ribod.storestudio.model

import kotlinx.serialization.Serializable

/**
 * Official documentation source reference validating a store specification.
 *
 * @property url Official documentation URL from Google or Apple.
 * @property verifiedOn ISO date string when this rule was last verified against developer documentation.
 * @property note Optional explanatory note or context.
 */
@Serializable
public data class SourceRef(
    public val url: String,
    public val verifiedOn: String,
    public val note: String? = null,
)
