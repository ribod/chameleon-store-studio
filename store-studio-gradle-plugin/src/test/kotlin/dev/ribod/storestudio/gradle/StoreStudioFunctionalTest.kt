package dev.ribod.storestudio.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Functional tests executing tasks in real Gradle daemon subprocesses via TestKit.
 */
class StoreStudioFunctionalTest {

    @get:Rule
    val testProjectDir: TemporaryFolder = TemporaryFolder()

    @Test
    fun executesStoreDoctorViaTestKitWithConfigurationCache() {
        val settingsFile = testProjectDir.newFile("settings.gradle.kts")
        settingsFile.writeText("""rootProject.name = "functional-test"""")

        val buildFile = testProjectDir.newFile("build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("dev.ribod.storestudio")
            }

            storeStudio {
                variant.set("debug")
                locales {
                    register("es-MX") {
                        playStore.set("es-419")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("storeDoctor", "storeDump", "--configuration-cache")
            .withPluginClasspath()
            .build()

        val doctorResult = result.task(":storeDoctor")
        val dumpResult = result.task(":storeDump")

        assertEquals(TaskOutcome.SUCCESS, doctorResult?.outcome)
        assertEquals(TaskOutcome.SUCCESS, dumpResult?.outcome)
        assertTrue(result.output.contains("Chameleon Store Studio: Doctor Report"))
        assertTrue(result.output.contains("Chameleon Store Studio: Configuration Matrix"))
        assertTrue(result.output.contains("Active Build Variant: debug"))
    }

    @Test
    fun executesStoreRenderPipelineViaTestKitWithConfigurationCache() {
        val settingsFile = testProjectDir.newFile("settings.gradle.kts")
        settingsFile.writeText("""rootProject.name = "render-pipeline-test"""")

        val buildFile = testProjectDir.newFile("build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("dev.ribod.storestudio")
            }

            repositories {
                google()
                mavenCentral()
            }

            storeStudio {
                variant.set("debug")
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("storeMergeManifest", "storeRender", "--configuration-cache")
            .withPluginClasspath()
            .build()

        val mergeResult = result.task(":storeMergeManifest")
        val renderResult = result.task(":storeRender")

        assertEquals(TaskOutcome.SUCCESS, mergeResult?.outcome)
        assertEquals(TaskOutcome.SUCCESS, renderResult?.outcome)

        val renderIndex = File(testProjectDir.root, "build/store/render/index.json")
        assertTrue("render/index.json must exist", renderIndex.exists())
        assertTrue(renderIndex.readText().contains("LAYOUTLIB"))
    }

    @Test
    fun executesStoreExportPipelineViaTestKitWithConfigurationCache() {
        val settingsFile = testProjectDir.newFile("settings.gradle.kts")
        settingsFile.writeText("""rootProject.name = "export-pipeline-test"""")

        val buildFile = testProjectDir.newFile("build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                id("dev.ribod.storestudio")
            }

            repositories {
                google()
                mavenCentral()
            }

            storeStudio {
                variant.set("debug")
                export {
                    background.set("#FFFFFF")
                    sizeTolerancePx.set(2)
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments("storeExport", "--configuration-cache")
            .withPluginClasspath()
            .build()

        val exportResult = result.task(":storeExport")
        assertEquals(TaskOutcome.SUCCESS, exportResult?.outcome)

        val exportIndex = File(testProjectDir.root, "build/store/export/index.json")
        assertTrue("export/index.json must exist", exportIndex.exists())
    }

    @Test
    fun executesStorePublishDryRunViaTestKitWithConfigurationCache() {
        testProjectDir.newFile("settings.gradle.kts")
            .writeText("""rootProject.name = "publish-dry-run-test"""")
        testProjectDir.newFile("build.gradle.kts").writeText(
            """
            plugins {
                id("dev.ribod.storestudio")
            }

            repositories {
                google()
                mavenCentral()
            }

            storeStudio {
                // No marketplaces: validation has nothing to require and publishing becomes a no-op,
                // while the task action still resolves its dry-run inputs at execution time.
                targets.set(emptySet())
                publishing {
                    googlePlay {
                        packageName.set("com.example.app")
                    }
                    appStore {
                        bundleId.set("com.example.app")
                    }
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments(
                "storePublish",
                "-PstoreStudio.publish.dryRun=true",
                "--configuration-cache",
            )
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":storePublish")?.outcome)
        assertTrue(result.output.contains("No matching marketplace targets"))
        assertTrue(result.output.contains("Configuration cache entry stored"))
    }
}
