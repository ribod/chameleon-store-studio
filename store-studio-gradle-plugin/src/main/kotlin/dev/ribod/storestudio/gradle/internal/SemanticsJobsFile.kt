package dev.ribod.storestudio.gradle.internal

import java.io.File

/** A render job as identified by the runtime semantics probe (`storestudio.semanticsJobsFile`). */
internal data class SemanticsJob(
    val jobId: String,
    val slotKey: String,
    val methodFqn: String,
    val slot: String,
    val target: String,
    val storeLocale: String,
)

/**
 * Writes the jobs file read by `StoreSemanticsProbe.jvm.kt`: one job per line, fields separated by
 * tabs in [SemanticsJob] order. The runtime has no JSON library, and none of the fields (ids,
 * JVM names, locale tags) can contain tabs or line breaks.
 */
internal fun writeSemanticsJobs(file: File, jobs: List<SemanticsJob>) {
    file.writeText(
        jobs.joinToString(separator = "\n", postfix = "\n") { job ->
            listOf(job.jobId, job.slotKey, job.methodFqn, job.slot, job.target, job.storeLocale)
                .onEach { field ->
                    require('\t' !in field && '\n' !in field) {
                        "Store Studio: render job field contains a tab or line break: '$field'"
                    }
                }
                .joinToString("\t")
        },
    )
}
