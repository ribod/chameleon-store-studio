package dev.ribod.storestudio.gradle.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.zip.ZipFile

/**
 * Unpacks the OS-specific `layoutlib-runtime` jar into the directory layout the renderer expects,
 * with `layoutlib-resources` copied to `data/framework_res.jar`.
 *
 * As an artifact transform of an external module, the result is cached once in the Gradle user
 * home and shared by every module and build, instead of a ~175 MB copy in each module's `build/`.
 */
@DisableCachingByDefault(because = "Unzipping is faster than loading the result from a cache")
internal abstract class UnpackLayoutlibTransform :
    TransformAction<UnpackLayoutlibTransform.Params> {

    internal interface Params : TransformParameters {
        /** The `layoutlib-resources` jar of the same Layoutlib version. */
        @get:Classpath
        val resourcesJar: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:Classpath
    abstract val runtimeJar: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val resources = parameters.resourcesJar.files.firstOrNull { it.name.endsWith(".jar") }
            ?: error("Store Studio: no layoutlib-resources jar resolved.")
        unpackLayoutlib(runtimeJar.get().asFile, resources, outputs.dir("layoutlib"))
    }

    companion object {
        private val ARTIFACT_TYPE: Attribute<String> =
            Attribute.of("artifactType", String::class.java)
        private const val UNPACKED = "store-studio-layoutlib"

        /**
         * Registers the transform and returns the unpacked Layoutlib directory of [runtime]
         * (a single directory once resolved).
         */
        fun register(
            project: Project,
            runtime: Configuration,
            resources: Configuration,
        ): FileCollection {
            project.dependencies.registerTransform(UnpackLayoutlibTransform::class.java) { spec ->
                spec.from.attribute(ARTIFACT_TYPE, "jar")
                spec.to.attribute(ARTIFACT_TYPE, UNPACKED)
                spec.parameters.resourcesJar.from(resources)
            }
            return runtime.incoming.artifactView { view ->
                view.attributes.attribute(ARTIFACT_TYPE, UNPACKED)
                view.componentFilter {
                    it is ModuleComponentIdentifier && it.module == "layoutlib-runtime"
                }
            }.files
        }
    }
}

/** Extracts [runtimeJar] into [outDir] and places [resourcesJar] at `data/framework_res.jar`. */
internal fun unpackLayoutlib(runtimeJar: File, resourcesJar: File, outDir: File) {
    outDir.deleteRecursively()
    outDir.mkdirs()
    val root = outDir.canonicalFile
    ZipFile(runtimeJar).use { zip ->
        for (entry in zip.entries()) {
            val target = File(root, entry.name).canonicalFile
            require(target.toPath().startsWith(root.toPath())) {
                "Store Studio: refusing to extract '${entry.name}' outside the Layoutlib directory"
            }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
    resourcesJar.copyTo(File(root, "data/framework_res.jar"), overwrite = true)
}
