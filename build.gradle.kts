plugins {
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.multiplatform) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

val gradleWrapper = if (System.getProperty("os.name").startsWith("Windows")) "gradlew.bat" else "./gradlew"

// The IDE plugin is a standalone build (it targets the Kotlin version bundled with the IDE);
// these tasks drive it from the repository root.
tasks.register<Exec>("checkIdeaPlugin") {
    group = "verification"
    description = "Runs `check` and `buildPlugin` in the standalone store-studio-idea build."
    workingDir = rootDir
    commandLine(gradleWrapper, "-p", "store-studio-idea", "check", "buildPlugin")
}

tasks.register<Exec>("runIdeaPlugin") {
    group = "ide plugin"
    description = "Starts a sandbox IDE with the Store Studio plugin installed."
    workingDir = rootDir
    commandLine(gradleWrapper, "-p", "store-studio-idea", "runIde")
}

// Samples are standalone builds that include this repository (includeBuild("../../")), so they
// consume the plugin and libraries from source. Every samples/<name> directory gets
// `<name>StoreDump`, `<name>StoreValidate` and `<name>StoreExport` tasks, e.g.
// `./gradlew sampleKotlinconfStoreExport`, plus an end-to-end `verifySamples`.
val sampleDirs = layout.projectDirectory.dir("samples").asFile
    .listFiles { dir -> dir.resolve("settings.gradle.kts").isFile }
    .orEmpty()
    .sortedBy { it.name }

fun sampleTaskPrefix(sample: File): String =
    sample.name.split('-').mapIndexed { index, part ->
        if (index == 0) part else part.replaceFirstChar { it.uppercase() }
    }.joinToString("")

val sampleExportTasks = sampleDirs.map { sample ->
    val prefix = sampleTaskPrefix(sample)
    listOf("storeDump", "storeValidate").forEach { storeTask ->
        tasks.register<Exec>("$prefix${storeTask.replaceFirstChar { it.uppercase() }}") {
            group = "samples"
            description = "Runs `$storeTask` in samples/${sample.name}."
            workingDir = rootDir
            commandLine(gradleWrapper, "-p", "samples/${sample.name}", storeTask)
        }
    }
    tasks.register<Exec>("${prefix}StoreExport") {
        group = "samples"
        description = "Renders and exports every module of samples/${sample.name}."
        workingDir = rootDir
        commandLine(gradleWrapper, "-p", "samples/${sample.name}", "storeExport", "--continue")
    }
}

tasks.register("verifySamples") {
    group = "verification"
    description = "Renders all samples end to end and checks that every module exported its screenshots."
    dependsOn(sampleExportTasks)
    val samples = sampleDirs.toList()
    doLast {
        val slurper = groovy.json.JsonSlurper()
        fun countOf(file: File, key: String): Int =
            if (file.isFile) ((slurper.parse(file) as Map<*, *>)[key] as? List<*>)?.size ?: 0 else 0

        val problems = mutableListOf<String>()
        for (sample in samples) {
            sample.walkTopDown()
                .onEnter { it.name != ".gradle" && it.name != "src" }
                .filter { it.path.endsWith("build/store/manifest/merged.json") }
                .forEach { merged ->
                    val storeDir = merged.parentFile.parentFile
                    val module = storeDir.parentFile.parentFile.relativeTo(sample).path.ifEmpty { "." }
                    val entries = countOf(merged, "entries")
                    val rendered = countOf(storeDir.resolve("render/index.json"), "jobs")
                    val exported = countOf(storeDir.resolve("export/index.json"), "assets")
                    logger.lifecycle(
                        "${sample.name}/$module: $entries entries, $rendered rendered, $exported exported",
                    )
                    if (entries > 0 && (exported == 0 || exported != rendered)) {
                        problems += "${sample.name}/$module exported $exported of $rendered rendered"
                    }
                }
        }
        if (problems.isNotEmpty()) {
            throw GradleException("Sample verification failed:\n" + problems.joinToString("\n"))
        }
    }
}
