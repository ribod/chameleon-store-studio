package dev.ribod.storestudio.core.publish

import java.io.File

/** Resolves [relative] under [exportDir]; null if it is not a regular file inside it. */
internal fun resolveExportFile(exportDir: File, relative: String): File? {
    val file = exportDir.resolve(relative)
    val inside = file.canonicalFile.toPath().startsWith(exportDir.canonicalFile.toPath())
    return file.takeIf { inside && it.isFile }
}

/** Failure message for the first of [relatives] that [resolveExportFile] rejects, else null. */
internal fun invalidExportFileMessage(exportDir: File, relatives: List<String>): String? =
    relatives.firstOrNull { resolveExportFile(exportDir, it) == null }
        ?.let { "Asset file is missing or outside the export directory: $it" }
