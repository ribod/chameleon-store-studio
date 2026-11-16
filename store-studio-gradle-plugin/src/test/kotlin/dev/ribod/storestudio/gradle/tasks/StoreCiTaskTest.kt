package dev.ribod.storestudio.gradle.tasks

import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.model.DiffChange
import dev.ribod.storestudio.model.DiffChangeType
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.DiffRefInfo
import dev.ribod.storestudio.model.DiffSummary
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.ValidationReport
import dev.ribod.storestudio.model.Violation
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreCiTaskTest {

    @get:Rule
    val tempFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun executeQualityGatePassesWhenNoDriftAndNoErrors() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testStoreCi", StoreCiTask::class.java).get()

        val dir = tempFolder.newFolder("ci-test")
        val diffFile = File(dir, "diff.json").apply {
            val doc = DiffDocument(
                base = DiffRefInfo(ref = "v1.0.0", source = "LOCAL_BASELINE"),
                head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
                summary = DiffSummary(),
                changes = emptyList(),
            )
            writeText(doc.toJson())
        }
        val diffMdFile = File(dir, "diff-summary.md").apply {
            writeText("### No changes")
        }
        val diffHtmlFile = File(dir, "diff.html").apply {
            writeText("<html></html>")
        }
        val valFile = File(dir, "report.json").apply {
            val report = ValidationReport(valid = true, violations = emptyList())
            writeText(report.toJson())
        }

        task.diffJsonFile.set(diffFile)
        task.diffSummaryMarkdownFile.set(diffMdFile)
        task.diffHtmlFile.set(diffHtmlFile)
        task.validationReportFile.set(valFile)
        task.failOnDrift.set(true)
        task.failOnValidationErrors.set(true)

        task.executeQualityGate()
    }

    @Test
    fun executeQualityGateFailsWhenVisualDriftDetectedAndFailOnDriftIsTrue() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testStoreCiDrift", StoreCiTask::class.java).get()

        val dir = tempFolder.newFolder("ci-drift-test")
        val diffFile = File(dir, "diff.json").apply {
            val doc = DiffDocument(
                base = DiffRefInfo(ref = "v1.0.0", source = "LOCAL_BASELINE"),
                head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
                summary = DiffSummary(modified = 2),
                changes = listOf(
                    DiffChange(
                        type = DiffChangeType.MODIFIED,
                        bucket = "PLAY_STORE/PHONE/en-US",
                        id = "screen_1",
                        basePosition = 1,
                        headPosition = 1,
                    ),
                ),
            )
            writeText(doc.toJson())
        }
        val diffMdFile = File(dir, "diff-summary.md").apply { writeText("Summary") }
        val diffHtmlFile = File(dir, "diff.html").apply { writeText("<html></html>") }
        val valFile = File(dir, "report.json").apply {
            writeText(ValidationReport(valid = true).toJson())
        }

        task.diffJsonFile.set(diffFile)
        task.diffSummaryMarkdownFile.set(diffMdFile)
        task.diffHtmlFile.set(diffHtmlFile)
        task.validationReportFile.set(valFile)
        task.failOnDrift.set(true)

        val ex = assertThrows(GradleException::class.java) {
            task.executeQualityGate()
        }
        assertTrue(ex.message!!.contains("visual difference(s) detected"))
    }

    @Test
    fun executeQualityGateFailsWhenComplianceErrorsExistAndFailOnValidationIsTrue() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("testStoreCiVal", StoreCiTask::class.java).get()

        val dir = tempFolder.newFolder("ci-val-test")
        val diffFile = File(dir, "diff.json").apply {
            writeText(
                DiffDocument(
                    base = DiffRefInfo(ref = "v1.0.0", source = "LOCAL_BASELINE"),
                    head = DiffRefInfo(ref = "HEAD", source = "CURRENT_EXPORT"),
                    summary = DiffSummary(),
                    changes = emptyList(),
                ).toJson(),
            )
        }
        val diffMdFile = File(dir, "diff-summary.md").apply { writeText("Summary") }
        val diffHtmlFile = File(dir, "diff.html").apply { writeText("<html></html>") }
        val valFile = File(dir, "report.json").apply {
            val report = ValidationReport(
                valid = false,
                violations = listOf(
                    Violation(
                        ruleId = "SCREENSHOT_DIMENSIONS",
                        severity = Severity.ERROR,
                        target = StoreTarget.PLAY_STORE,
                        message = "Invalid dimension 100x100",
                    ),
                ),
            )
            writeText(report.toJson())
        }

        task.diffJsonFile.set(diffFile)
        task.diffSummaryMarkdownFile.set(diffMdFile)
        task.diffHtmlFile.set(diffHtmlFile)
        task.validationReportFile.set(valFile)
        task.failOnValidationErrors.set(true)

        val ex = assertThrows(GradleException::class.java) {
            task.executeQualityGate()
        }
        assertTrue(ex.message!!.contains("store compliance error(s) found"))
    }
}
