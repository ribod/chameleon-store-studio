import com.android.build.api.dsl.LibraryExtension
import dev.ribod.storestudio.buildlogic.configureAndroidLibrary
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("dev.ribod.storestudio.spotless")

            val extension = extensions.getByType<LibraryExtension>()
            configureAndroidLibrary(extension)
            extension.apply {
                buildFeatures {
                    compose = true
                }
            }
        }
    }
}
