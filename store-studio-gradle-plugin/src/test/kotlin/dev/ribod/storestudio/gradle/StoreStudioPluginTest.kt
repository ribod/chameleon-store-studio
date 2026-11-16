package dev.ribod.storestudio.gradle

import dev.ribod.storestudio.annotations.DeviceFamily
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.gradle.dsl.BaselineComparison
import dev.ribod.storestudio.gradle.dsl.RenderingEngine
import dev.ribod.storestudio.gradle.dsl.StoreStudioExtension
import dev.ribod.storestudio.gradle.tasks.ParseStoreMetadataTask
import dev.ribod.storestudio.gradle.tasks.StoreDoctorTask
import dev.ribod.storestudio.gradle.tasks.StoreDumpTask
import dev.ribod.storestudio.gradle.tasks.StorePublishTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying extension DSL defaults, task registration, and configuration wiring.
 */
class StoreStudioPluginTest {

    private fun parseTask(project: org.gradle.api.Project) =
        project.tasks.getByName("storeParseMetadata") as ParseStoreMetadataTask

    @Test
    fun iconsAreResolvedByConventionWithCrossStoreFallbackForPreviews() {
        val dir = java.nio.file.Files.createTempDirectory("icons").toFile()
        java.io.File(dir, "iosApp/Assets.xcassets/AppIcon.appiconset/icon.png")
            .apply {
                parentFile.mkdirs()
                writeBytes(ByteArray(4))
            }
        val project = ProjectBuilder.builder().withProjectDir(dir).build()
        project.pluginManager.apply("dev.ribod.storestudio")

        // No Android icon exists, so the Google Play preview borrows the iOS one
        val expected = "iosApp/Assets.xcassets/AppIcon.appiconset/icon.png"
        assertEquals(expected, parseTask(project).googlePlayIcon.get())
        assertEquals(expected, parseTask(project).appStoreIcon.get())
    }

    @Test
    fun configuredIconsOverrideConventionPerStore() {
        val dir = java.nio.file.Files.createTempDirectory("icons").toFile()
        java.io.File(dir, "iosApp/Assets.xcassets/AppIcon.appiconset/icon.png")
            .apply {
                parentFile.mkdirs()
                writeBytes(ByteArray(4))
            }
        val custom = java.io.File(dir, "art/play.png").apply {
            parentFile.mkdirs()
            writeBytes(ByteArray(4))
        }
        val project = ProjectBuilder.builder().withProjectDir(dir).build()
        project.pluginManager.apply("dev.ribod.storestudio")
        project.extensions.getByType(StoreStudioExtension::class.java).icons {
            it.googlePlay.set(custom)
        }

        assertEquals("art/play.png", parseTask(project).googlePlayIcon.get())
        assertEquals(
            "iosApp/Assets.xcassets/AppIcon.appiconset/icon.png",
            parseTask(project).appStoreIcon.get(),
        )
    }

    @Test
    fun noIconAnywhereLeavesBothStoresEmpty() {
        val project = ProjectBuilder.builder().withProjectDir(
            java.nio.file.Files.createTempDirectory("icons").toFile(),
        ).build()
        project.pluginManager.apply("dev.ribod.storestudio")

        org.junit.Assert.assertFalse(parseTask(project).googlePlayIcon.isPresent)
        org.junit.Assert.assertFalse(parseTask(project).appStoreIcon.isPresent)
    }

    @Test
    fun appliesExtensionWithSensibleDefaults() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val extension = project.extensions.findByType(StoreStudioExtension::class.java)
        assertNotNull(extension)
        requireNotNull(extension)

        assertEquals("debug", extension.variant.get())
        assertEquals(
            setOf(StoreTarget.PLAY_STORE, StoreTarget.APP_STORE),
            extension.targets.get(),
        )
        assertEquals(
            setOf(DeviceFamily.PHONE, DeviceFamily.TABLET),
            extension.defaultDevices.get(),
        )
        assertEquals(RenderingEngine.LAYOUTLIB, extension.rendering.engine.get())
        assertEquals(RenderingEngine.ROBOLECTRIC, extension.rendering.fallbackEngine.get())
        assertEquals(0.05, extension.baseline.perceptualThreshold.get(), 0.0001)
        assertTrue(extension.baseline.failOnDrift.get())
        assertEquals("#FFFFFF", extension.export.background.get())
        assertEquals(2, extension.export.sizeTolerancePx.get())
        org.junit.Assert.assertFalse(extension.validation.failOnWarning.get())
        assertEquals(
            dev.ribod.storestudio.model.Severity.ERROR,
            extension.validation.featureGraphicMissing.get(),
        )
        assertEquals(
            dev.ribod.storestudio.core.report.ReportImageMode.THUMBNAIL,
            extension.reports.imageMode.get(),
        )
        assertTrue(extension.reports.htmlGallery.get())
        assertTrue(extension.reports.storeListingPreview.get())
        assertEquals(720, extension.reports.maxThumbnailDimension.get())
        org.junit.Assert.assertFalse(extension.ci.failOnDrift.get())
        assertTrue(extension.ci.failOnValidationErrors.get())
        org.junit.Assert.assertFalse(extension.ci.postPrComment.get())
        // One sticky comment per project: <root project name><project path>
        assertEquals(
            "<!-- STORE_STUDIO_DIFF_COMMENT ${project.rootProject.name}: -->",
            extension.ci.commentMarker.get(),
        )
    }

    @Test
    fun registersAllLifecycleAndPipelineTasks() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val expectedTasks = listOf(
            "storeDoctor",
            "storeDump",
            "storePrepareLayoutlib",
            "storeMergeManifest",
            "storeRender",
            "storeExport",
            "exportStoreAssets",
            "storeValidate",
            "storeVerify",
            "storeRecordBaseline",
            "storeDiff",
            "exportStoreDiffHtml",
            "storeDiffReport",
            "storeCi",
            "storeCiCheck",
            "exportStoreGalleryHtml",
            "storeGallery",
            "exportStoreListingPreviewHtml",
            "storeListingPreview",
            "storePublishPlay",
            "storePublishPlayListing",
            "storePublishPlayTexts",
            "storePublishPlayReleaseNotes",
            "storePublishPlayImages",
            "storePublishPlayBinary",
            "storePublishAppStore",
            "storePublishAppStoreListing",
            "storePublishAppStoreTexts",
            "storePublishAppStoreReleaseNotes",
            "storePublishAppStoreImages",
            "storePublishAppStoreBinary",
            "storePublish",
            "storePublishListing",
            "storePublishTexts",
            "storePublishImages",
            "storePublishReleaseNotes",
            "storePublishBinary",
            "storeStudio",
            "storeParseMetadata",
            "storeExportMetadata",
        )

        for (taskName in expectedTasks) {
            assertNotNull(
                "Task '$taskName' must be registered on the project",
                project.tasks.findByName(taskName),
            )
        }

        val parseMetadataTask = project.tasks.findByName("storeParseMetadata")
        assertTrue(parseMetadataTask is ParseStoreMetadataTask)

        val publishTask = project.tasks.findByName("storePublish")
        assertTrue(publishTask is StorePublishTask)
        assertTrue((publishTask as StorePublishTask).includeBinary.get())

        val publishListingTask = project.tasks.findByName("storePublishListing")
        assertTrue(publishListingTask is StorePublishTask)
        org.junit.Assert.assertFalse((publishListingTask as StorePublishTask).includeBinary.get())

        val playTask = project.tasks.findByName(
            "storePublishPlay",
        ) as dev.ribod.storestudio.gradle.tasks.StorePublishPlayTask
        assertTrue(playTask.includeBinary.get())

        val playListingTask = project.tasks.findByName(
            "storePublishPlayListing",
        ) as dev.ribod.storestudio.gradle.tasks.StorePublishPlayTask
        org.junit.Assert.assertFalse(playListingTask.includeBinary.get())

        val appStoreTask = project.tasks.findByName(
            "storePublishAppStore",
        ) as dev.ribod.storestudio.gradle.tasks.StorePublishAppStoreTask
        assertTrue(appStoreTask.includeBinary.get())

        val appStoreListingTask = project.tasks.findByName(
            "storePublishAppStoreListing",
        ) as dev.ribod.storestudio.gradle.tasks.StorePublishAppStoreTask
        org.junit.Assert.assertFalse(appStoreListingTask.includeBinary.get())
    }

    @Test
    fun configuresLocalesAndStoreSpecificCodes() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val extension = project.extensions.getByType(StoreStudioExtension::class.java)
        extension.locales.register("en-US")
        extension.locales.register("es-MX") { spec ->
            spec.playStore.set("es-419")
        }

        assertEquals(2, extension.locales.size)
        val esMx = extension.locales.getByName("es-MX")
        assertEquals("es-419", esMx.playStore.get())
    }

    @Test
    fun executesDoctorAndDumpTasksSuccessfully() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val doctor = project.tasks.getByName("storeDoctor") as StoreDoctorTask
        doctor.checkEnvironment()

        val dump = project.tasks.getByName("storeDump") as StoreDumpTask
        dump.dumpMatrix()
    }

    @Test
    fun executesMergeManifestTaskWithEmptyManifestFiles() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val mergeTask = project.tasks.getByName(
            "storeMergeManifest",
        ) as dev.ribod.storestudio.gradle.tasks.MergeStoreManifestTask
        mergeTask.merge()

        val manifestFile = mergeTask.mergedManifest.get().asFile
        assertTrue(manifestFile.exists())
        val content = manifestFile.readText()
        assertTrue(content.contains("consolidated"))
        assertTrue(content.contains("entries"))
    }

    @Test
    fun mergeManifestCollapsesSameDeclarationFromMultipleCompilations() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        fun manifest(name: String, line: Int): java.io.File {
            val entry = dev.ribod.storestudio.model.ManifestEntry(
                kind = dev.ribod.storestudio.model.EntryKind.SCREENSHOT,
                id = "hero",
                functionFqn = "com.example.ScreensKt.Hero",
                packageName = "com.example",
                functionName = "Hero",
                source = dev.ribod.storestudio.model.SourceLocation("src/Screens.kt", line),
            )
            val manifest = dev.ribod.storestudio.model.StoreManifest(
                schemaVersion = 1,
                module = ":shared",
                generator = "test",
                entries = listOf(entry),
            )
            return project.file("$name/store-manifest.json").apply {
                parentFile.mkdirs()
                writeText(dev.ribod.storestudio.model.StoreManifest.encodeToString(manifest))
            }
        }

        val mergeTask = project.tasks.getByName(
            "storeMergeManifest",
        ) as dev.ribod.storestudio.gradle.tasks.MergeStoreManifestTask
        mergeTask.manifestFiles.from(manifest("android", 10), manifest("metadata", 10))
        mergeTask.merge()

        val merged = dev.ribod.storestudio.model.StoreManifest.decodeFromString(
            mergeTask.mergedManifest.get().asFile.readText(),
        )
        assertEquals(1, merged.entries.size)

        mergeTask.manifestFiles.from(manifest("other", 42))
        val failure = runCatching { mergeTask.merge() }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure!!.message!!.contains("Duplicate screenshot identifier 'hero'"))
    }

    @Test
    fun executesRenderTaskWithEmptyManifest() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val mergeTask = project.tasks.getByName(
            "storeMergeManifest",
        ) as dev.ribod.storestudio.gradle.tasks.MergeStoreManifestTask
        mergeTask.merge()

        val renderTask = project.tasks.getByName(
            "storeRender",
        ) as dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask
        renderTask.render()

        val indexFile = renderTask.renderIndex.get().asFile
        assertTrue(indexFile.exists())
        val index = dev.ribod.storestudio.model.RenderIndex.decodeFromString(
            indexFile.readText(),
        )
        assertEquals("LAYOUTLIB", index.engineDefault)
        assertEquals(0, index.jobs.size)

        val failedJobsFile = renderTask.failedJobsFile.get().asFile
        assertTrue(failedJobsFile.exists())
        val failed = dev.ribod.storestudio.model.FailedJobsManifest.decodeFromString(
            failedJobsFile.readText(),
        )
        assertEquals(0, failed.jobs.size)
    }

    @Test
    fun layoutlibDependenciesHonorVersionsConfiguredAfterApply() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")
        val extension = project.extensions.getByType(StoreStudioExtension::class.java)

        extension.rendering.layoutlibVersion.set("99.0.0")
        extension.rendering.rendererVersion.set("88.0.0")

        fun coordinates(configuration: String): List<String> =
            project.configurations.getByName(configuration).dependencies
                .map { "${it.group}:${it.name}:${it.version}" }

        assertTrue(
            coordinates("storeStudioLayoutlibRenderer")
                .contains("com.android.tools.layoutlib:layoutlib:99.0.0"),
        )
        assertTrue(
            coordinates("storeStudioLayoutlibRenderer")
                .contains("com.android.tools.compose:compose-preview-renderer:88.0.0"),
        )
        assertTrue(
            coordinates("storeStudioLayoutlibResources")
                .contains("com.android.tools.layoutlib:layoutlib-resources:99.0.0"),
        )
        assertTrue(
            coordinates("storeStudioLayoutlibRuntime").single().contains(":99.0.0"),
        )
    }

    @Test
    fun libraryResourcesAreConfiguredByDefault() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")
        val extension = project.extensions.getByType(StoreStudioExtension::class.java)

        assertTrue(extension.rendering.configureLibraryResources.get())
        assertEquals(java.time.Duration.ofMinutes(5), extension.rendering.fallbackTimeout.get())
    }

    @Test
    fun libraryModuleWithoutLinkedResourcesSkipsRenderingInsteadOfFailing() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val manifest = dev.ribod.storestudio.model.StoreManifest(
            schemaVersion = 1,
            module = ":feature",
            generator = "test",
            entries = listOf(
                dev.ribod.storestudio.model.ManifestEntry(
                    kind = dev.ribod.storestudio.model.EntryKind.SCREENSHOT,
                    id = "home",
                    functionFqn = "com.example.ScreensKt.Home",
                    packageName = "com.example",
                    functionName = "Home",
                    source = dev.ribod.storestudio.model.SourceLocation("src/Screens.kt", 3),
                ),
            ),
        )
        val renderTask = project.tasks.getByName(
            "storeRender",
        ) as dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask
        renderTask.manifestFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(dev.ribod.storestudio.model.StoreManifest.encodeToString(manifest))
        }
        renderTask.libraryModule.set(true)

        renderTask.render()

        val index = dev.ribod.storestudio.model.RenderIndex.decodeFromString(
            renderTask.renderIndex.get().asFile.readText(),
        )
        assertEquals(0, index.jobs.size)
        val failed = dev.ribod.storestudio.model.FailedJobsManifest.decodeFromString(
            renderTask.failedJobsFile.get().asFile.readText(),
        )
        assertEquals(0, failed.jobs.size)
    }

    @Test
    fun renderOutputsHoldNoAbsolutePathsSoTheyCanBeCached() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")

        val manifest = dev.ribod.storestudio.model.StoreManifest(
            schemaVersion = 1,
            module = ":app",
            generator = "test",
            entries = listOf(
                dev.ribod.storestudio.model.ManifestEntry(
                    kind = dev.ribod.storestudio.model.EntryKind.SCREENSHOT,
                    id = "home",
                    functionFqn = "com.example.ScreensKt.Home",
                    packageName = "com.example",
                    functionName = "Home",
                    source = dev.ribod.storestudio.model.SourceLocation("src/Screens.kt", 3),
                ),
            ),
        )
        val renderTask = project.tasks.getByName(
            "storeRender",
        ) as dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask
        renderTask.manifestFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(dev.ribod.storestudio.model.StoreManifest.encodeToString(manifest))
        }

        // No renderer classpath: every job fails and is handed to the fallback
        renderTask.rendererClasspath.setFrom()
        renderTask.render()

        val root = project.projectDir.absolutePath
        val failedJobs = renderTask.failedJobsFile.get().asFile.readText()
        val failed = dev.ribod.storestudio.model.FailedJobsManifest.decodeFromString(
            failedJobs,
        )
        assertTrue(failed.jobs.isNotEmpty())
        assertTrue(failed.jobs.all { !java.io.File(it.outputPath).isAbsolute })
        assertTrue(root !in failedJobs)
        assertTrue(root !in renderTask.renderIndex.get().asFile.readText())
    }

    @Test
    fun statusBarIsOffByDefaultAndSerializedWhenEnabled() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.ribod.storestudio")
        val extension = project.extensions.getByType(StoreStudioExtension::class.java)
        val renderTask = project.tasks.getByName(
            "storeRender",
        ) as dev.ribod.storestudio.gradle.tasks.LayoutlibRenderTask
        assertEquals(null, renderTask.statusBar.orNull)

        extension.statusBar {
            it.enabled.set(true)
            it.style.set(dev.ribod.storestudio.gradle.dsl.StatusBarStyleKind.IOS)
            it.wifi.set(2)
            it.battery.set(15)
            it.charging.set(true)
            it.showCellular.set(false)
            it.time.set("10;05")
        }
        assertEquals(
            "style=ios;time=10%3B05;wifi=2;battery=15;charging=true;dark=false",
            renderTask.statusBar.get(),
        )

        extension.statusBar.wifi.set(7)
        val error = org.junit.Assert.assertThrows(RuntimeException::class.java) {
            renderTask.statusBar.get()
        }
        generateSequence<Throwable>(error) { it.cause }
            .any { it is org.gradle.api.GradleException && it.message!!.contains("wifi") }
            .let(::assertTrue)
    }
}
