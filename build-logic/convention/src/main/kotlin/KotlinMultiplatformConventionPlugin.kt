import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class KotlinMultiplatformConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("dev.ribod.storestudio.spotless")

            extensions.configure<KotlinMultiplatformExtension> {
                jvm()
                jvmToolchain {
                    languageVersion.set(JavaLanguageVersion.of(17))
                }
                applyDefaultHierarchyTemplate()
                compilerOptions {
                    freeCompilerArgs.addAll(
                        "-opt-in=kotlin.RequiresOptIn",
                    )
                }
            }
        }
    }
}
