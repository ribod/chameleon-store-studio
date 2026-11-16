import com.diffplug.gradle.spotless.SpotlessExtension
import dev.ribod.storestudio.buildlogic.isIncludedBuild
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

// Build outputs and tool-generated caches never contain hand-written sources
private val GENERATED_DIRS = arrayOf("**/build/**", "**/bin/**", "**/.gradle/**", "**/.intellijPlatform/**")

class SpotlessConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        // Formatting is only checked when developing this repository, not when it is included
        if (target.isIncludedBuild) return
        with(target) {
            pluginManager.apply("com.diffplug.spotless")

            extensions.configure<SpotlessExtension> {
                kotlin {
                    target("src/**/*.kt")
                    targetExclude(*GENERATED_DIRS)
                    ktlint().editorConfigOverride(
                        mapOf(
                            "indent_size" to "4",
                            "continuation_indent_size" to "4",
                            "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                        ),
                    )
                    trimTrailingWhitespace()
                    endWithNewline()
                }

                kotlinGradle {
                    target("*.gradle.kts")
                    targetExclude(*GENERATED_DIRS)
                    ktlint().editorConfigOverride(
                        mapOf(
                            "indent_size" to "4",
                            "continuation_indent_size" to "4",
                        ),
                    )
                    trimTrailingWhitespace()
                    endWithNewline()
                }

                format("xml") {
                    target("src/**/*.xml", "*.xml", ".run/*.xml")
                    targetExclude(*GENERATED_DIRS)
                    trimTrailingWhitespace()
                    endWithNewline()
                }
            }
        }
    }
}
