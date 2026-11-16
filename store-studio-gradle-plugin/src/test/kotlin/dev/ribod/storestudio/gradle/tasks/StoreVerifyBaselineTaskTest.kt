package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.model.BaselineComparison
import dev.ribod.storestudio.model.ExportIndex
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreVerifyBaselineTaskTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun verifyTask(required: Boolean): StoreVerifyBaselineTask {
        val projectDir = tempFolder.newFolder()
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.register("storeVerify", StoreVerifyBaselineTask::class.java).get()
        val index = File(projectDir, "build/store/export/index.json")
        ExportIndex(schemaVersion = 1, assets = emptyList()).toFile(index)
        task.exportIndexFile.set(index)
        task.baselineFile.set(File(projectDir, ".store/baseline.json"))
        task.comparison.set(BaselineComparison.STRICT)
        task.perceptualThreshold.set(0.05)
        task.failOnDrift.set(true)
        task.required.set(required)
        task.reportFile.set(File(projectDir, "build/store/verification/report.json"))
        return task
    }

    @Test
    fun skipsWhenNoBaselineIsRecorded() {
        val task = verifyTask(required = false)

        task.verify()

        assertFalse(task.reportFile.get().asFile.exists())
    }

    @Test
    fun failsWhenABaselineIsRequired() {
        val task = verifyTask(required = true)

        val error = assertThrows(GradleException::class.java) { task.verify() }

        assertTrue(error.message!!.contains("storeRecordBaseline"))
    }
}
