import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Publishes a module to Maven Central (and Maven Local) with the shared POM metadata.
 *
 * The module's `description` becomes the POM description. Credentials and the signing key are
 * read by the vanniktech plugin from `mavenCentralUsername`, `mavenCentralPassword`,
 * `signingInMemoryKey`, `signingInMemoryKeyId` and `signingInMemoryKeyPassword` (Gradle
 * properties, or `ORG_GRADLE_PROJECT_*` environment variables in CI).
 */
class PublishingConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.vanniktech.maven.publish")
            extensions.configure<MavenPublishBaseExtension> {
                coordinates(group.toString(), name, version.toString())
                // Deployments are reviewed in the Central Portal before they go live
                publishToMavenCentral(automaticRelease = false)
                // Signing is only possible with a key; without one, publishToMavenLocal still works
                if (providers.gradleProperty("signingInMemoryKey").isPresent) {
                    signAllPublications()
                }
                pom {
                    name.set(project.name)
                    description.set(provider { project.description })
                    url.set("https://github.com/ribod/chameleon-store-studio")
                    inceptionYear.set("2026")
                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("ribod")
                            name.set("ribod")
                            url.set("https://github.com/ribod")
                        }
                    }
                    scm {
                        url.set("https://github.com/ribod/chameleon-store-studio")
                        connection.set("scm:git:git://github.com/ribod/chameleon-store-studio.git")
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/ribod/chameleon-store-studio.git",
                        )
                    }
                }
            }
        }
    }
}
