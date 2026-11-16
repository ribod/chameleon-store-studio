package dev.ribod.storestudio.model

import kotlinx.serialization.json.Json

/** Format of the build artifacts written by Store Studio (reports, indexes, snapshots). */
internal val ArtifactJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Like [ArtifactJson], but omits absent optional values (manifests, render and job indexes). */
internal val ManifestJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
}
