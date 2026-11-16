import dev.ribod.storestudio.buildlogic.configureKotlinJvm
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType

class GradlePluginConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("java-gradle-plugin")
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("dev.ribod.storestudio.spotless")

            configureKotlinJvm()

            tasks.withType<Test>().configureEach {
                useJUnit()
            }
        }
    }
}
