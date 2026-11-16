package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.BaselineAsset
import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.BaselineManifest
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ExportIndex
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreDiffTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun registersAndConfiguresStoreDiffTask() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("storeDiff", StoreDiffTask::class.java).get()

        assertNotNull(task)
        assertEquals("store studio", task.group)
        assertEquals(false, task.renderBase.get())
    }

    @Test
    fun executesDiffAgainstLocalBaselineFile() {
        val testProjectDir = tempFolder.newFolder("project")
        val project = ProjectBuilder.builder().withProjectDir(testProjectDir).build()
        val task = project.tasks.register("storeDiff", StoreDiffTask::class.java).get()

        val exportDir = File(testProjectDir, "build/store/export")
        exportDir.mkdirs()
        val dummyImg = File(exportDir, "01_hero.png").apply {
            writeBytes(ByteArray(100))
        }

        val exportIndexFile = File(exportDir, "index.json")
        val exportIndex = ExportIndex(
            schemaVersion = 1,
            assets = listOf(
                ExportAsset(
                    slotKey = "PLAY_STORE/PHONE/en-US/hero",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    id = "hero",
                    order = 1,
                    position = 1,
                    file = "01_hero.png",
                    format = ImageFormat.PNG,
                    widthPx = 1080,
                    heightPx = 1920,
                    bytes = dummyImg.length(),
                    pixelSha256 = "head_pixel_sha",
                    fileSha256 = "head_file_sha",
                    md5 = "head_md5",
                    pHash = "0000000000000000",
                ),
            ),
        )
        exportIndex.toFile(exportIndexFile)

        val baselineFile = File(testProjectDir, ".store/baseline.json")
        val baseline = BaselineManifest(
            schemaVersion = 1,
            assets = listOf(
                BaselineAsset(
                    slotKey = "PLAY_STORE/PHONE/en-US/hero",
                    position = 1,
                    widthPx = 1080,
                    heightPx = 1920,
                    pixelSha256 = "base_pixel_sha",
                    pHash = "ffffffffffffffff",
                ),
            ),
        )
        baseline.toFile(baselineFile)

        val diffJsonFile = File(testProjectDir, "build/store/diff/diff.json")
        val beforeImagesDir = File(testProjectDir, "build/store/diff/base-images")
        val cacheDir = File(testProjectDir, ".gradle/cache")

        task.projectDir.set(testProjectDir)
        task.headExportIndexFile.set(exportIndexFile)
        task.headExportDirectory.set(exportDir)
        task.baselineFile.set(baselineFile)
        task.comparison.set(BaselineComparison.PERCEPTUAL)
        task.perceptualThreshold.set(0.05)
        task.cacheDirectory.set(cacheDir)
        task.diffJsonFile.set(diffJsonFile)
        task.beforeImagesDir.set(beforeImagesDir)

        task.diff()

        assertTrue(diffJsonFile.exists())
        val diffDoc = DiffDocument.fromFile(diffJsonFile)
        assertEquals(1, diffDoc.summary.modified)
        assertEquals(0, diffDoc.summary.added)
        assertEquals(1, diffDoc.changes.size)
        assertEquals(DiffChangeType.MODIFIED, diffDoc.changes.first().type)
        assertEquals("hero", diffDoc.changes.first().id)
    }

    @Test
    fun comparesAgainstBaseExportWithoutCommittedBaseline() {
        val root = tempFolder.newFolder("export-base")
        val project = ProjectBuilder.builder().withProjectDir(root).build()
        val task = project.tasks.register("storeDiff", StoreDiffTask::class.java).get()

        val headDir = writeExport(
            File(root, "build/store/export"),
            pixelSha = "same_pixels",
            textSha = "sha_meet",
            texts = listOf("Meet the Speakers"),
        )
        val baseDir = writeExport(
            File(root, "base-export"),
            pixelSha = "same_pixels",
            textSha = "sha_world",
            texts = listOf("World-Class Speakers"),
        )
        configure(task, root, headDir)
        task.baseExportDir.set(baseDir)

        task.diff()

        val diff = DiffDocument.fromFile(task.diffJsonFile.get().asFile)
        assertEquals("BASE_EXPORT", diff.base.source)
        val change = diff.changes.single()
        assertEquals(DiffChangeType.MODIFIED, change.type)
        assertEquals(listOf("Meet the Speakers"), change.addedTexts)
        assertTrue(File(change.baseImage!!).exists())
    }

    @Test
    fun rendersTheBaseRevisionOnceInATemporaryWorktree() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val repo = tempFolder.newFolder("repo")
        // A stand-in wrapper: "storeExport" copies the export committed at that revision
        File(repo, "gradlew").apply {
            writeText(
                "#!/bin/sh\n" +
                    "echo \"$@\" >> \"${repo.absolutePath}/wrapper-calls.txt\"\n" +
                    "mkdir -p app/build/store\n" +
                    "cp -R app/fixture-export app/build/store/export\n",
            )
            setExecutable(true)
        }
        File(repo, ".gitignore").writeText("build/\nwrapper-calls.txt\n")
        val appDir = File(repo, "app")
        writeExport(
            File(appDir, "fixture-export"),
            pixelSha = "purple",
            textSha = "sha_world",
            texts = listOf("World-Class Speakers"),
        )
        git(repo, "init", "-q", "-b", "main")
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", "base")

        val root = ProjectBuilder.builder().withProjectDir(repo).build()
        val app = ProjectBuilder.builder().withName(
            "app",
        ).withProjectDir(appDir).withParent(root).build()
        val task = app.tasks.register("storeDiff", StoreDiffTask::class.java).get()
        val headDir = writeExport(
            File(appDir, "build/store/export"),
            pixelSha = "green",
            textSha = "sha_world",
            texts = listOf("World-Class Speakers"),
        )
        configure(task, appDir, headDir)
        task.rootProjectDir.set(repo)
        task.projectPath.set(":app")
        task.againstRef.set("main")
        task.renderBase.set(true)

        task.diff()

        val diff = DiffDocument.fromFile(task.diffJsonFile.get().asFile)
        assertEquals("RENDERED_REF", diff.base.source)
        assertEquals(DiffChangeType.MODIFIED, diff.changes.single().type)
        val calls = File(repo, "wrapper-calls.txt").readLines()
        assertEquals(1, calls.size)
        assertTrue(calls.single().contains(":app:storeExport"))
        assertFalse(File(appDir, "build/store/diff/base-render/worktree").exists())
        assertFalse(git(repo, "worktree", "list").contains("base-render"))
    }

    @Test
    fun treatsABaseWithoutStoreStudioAsEmpty() {
        assumeFalse(System.getProperty("os.name").startsWith("Windows"))
        val repo = tempFolder.newFolder("repo-without-plugin")
        File(repo, "gradlew").apply {
            writeText(
                "#!/bin/sh\necho \"Task 'storeExport' not found in project ':app'.\"\nexit 1\n",
            )
            setExecutable(true)
        }
        File(repo, ".gitignore").writeText("build/\n")
        val appDir = File(repo, "app").apply { mkdirs() }
        File(appDir, "README").writeText("app")
        git(repo, "init", "-q", "-b", "main")
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", "base")

        val root = ProjectBuilder.builder().withProjectDir(repo).build()
        val app = ProjectBuilder.builder().withName(
            "app",
        ).withProjectDir(appDir).withParent(root).build()
        val task = app.tasks.register("storeDiff", StoreDiffTask::class.java).get()
        configure(task, appDir, writeExport(File(appDir, "build/store/export"), pixelSha = "new"))
        task.rootProjectDir.set(repo)
        task.projectPath.set(":app")
        task.againstRef.set("main")
        task.renderBase.set(true)

        task.diff()

        val diff = DiffDocument.fromFile(task.diffJsonFile.get().asFile)
        assertEquals("EMPTY", diff.base.source)
        assertEquals(1, diff.summary.added)
    }

    private fun configure(task: StoreDiffTask, projectDir: File, headDir: File) {
        task.projectDir.set(projectDir)
        task.rootProjectDir.set(projectDir)
        task.projectPath.set(":")
        task.headExportIndexFile.set(File(headDir, "index.json"))
        task.headExportDirectory.set(headDir)
        task.baselineFile.set(File(projectDir, ".store/baseline.json"))
        task.comparison.set(BaselineComparison.STRICT)
        task.cacheDirectory.set(File(projectDir, ".gradle/cache"))
        task.workDirectory.set(File(projectDir, "build/store/diff/base-render"))
        task.diffJsonFile.set(File(projectDir, "build/store/diff/diff.json"))
        task.beforeImagesDir.set(File(projectDir, "build/store/diff/base-images"))
    }

    private fun writeExport(
        dir: File,
        pixelSha: String,
        textSha: String? = null,
        texts: List<String> = emptyList(),
    ): File {
        dir.mkdirs()
        val image = File(dir, "01_speakers.png").apply { writeBytes(ByteArray(10)) }
        ExportIndex(
            schemaVersion = 1,
            assets = listOf(
                ExportAsset(
                    slotKey = "PLAY_STORE/PHONE/en-US/speakers",
                    target = StoreTarget.PLAY_STORE,
                    deviceSlot = StoreDeviceSlot.PHONE,
                    storeLocale = "en-US",
                    id = "speakers",
                    order = 1,
                    position = 1,
                    file = image.name,
                    format = ImageFormat.PNG,
                    widthPx = 1080,
                    heightPx = 1920,
                    bytes = image.length(),
                    pixelSha256 = pixelSha,
                    fileSha256 = "file_$pixelSha",
                    md5 = "md5_$pixelSha",
                    pHash = "0000000000000000",
                    textSha256 = textSha,
                    extractedTexts = texts,
                ),
            ),
        ).toFile(File(dir, "index.json"))
        return dir
    }

    private fun git(dir: File, vararg args: String): String {
        val process = ProcessBuilder(
            listOf("git", "-c", "user.name=test", "-c", "user.email=test@example.com") + args,
        ).directory(dir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $output" }
        return output
    }
}
