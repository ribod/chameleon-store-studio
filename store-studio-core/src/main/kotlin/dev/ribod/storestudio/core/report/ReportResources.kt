package dev.ribod.storestudio.core.report

/**
 * Loads embedded report styling, scripts, and layout templates from classpath resources.
 */
internal object ReportResources {
    private const val RESOURCE_PREFIX = "/dev/ribod/storestudio/core/report/"

    /**
     * Reads the UTF-8 text contents of resource [name] normalized to UNIX newlines.
     */
    fun text(name: String): String {
        val path = RESOURCE_PREFIX + name
        val stream = ReportResources::class.java.getResourceAsStream(path)
            ?: error("Missing report classpath resource '$name' at $path")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
            .replace("\r\n", "\n")
    }
}
