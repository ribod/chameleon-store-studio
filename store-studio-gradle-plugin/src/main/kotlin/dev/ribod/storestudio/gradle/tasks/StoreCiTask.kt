package dev.ribod.storestudio.gradle.tasks

import com.skydoves.sandwich.ktor.statusCode
import com.skydoves.sandwich.suspendOnError
import com.skydoves.sandwich.suspendOnException
import com.skydoves.sandwich.suspendOnSuccess
import dev.ribod.storestudio.core.ci.CiPlatform
import dev.ribod.storestudio.core.ci.PrCommentClient
import dev.ribod.storestudio.core.ci.PrCommentTarget
import dev.ribod.storestudio.model.DiffDocument
import dev.ribod.storestudio.model.Severity
import dev.ribod.storestudio.model.ValidationReport
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

/**
 * Universal CI/CD quality gate task verifying store compliance, visual differences,
 * step summaries, and PR commenting.
 */
@CacheableTask
public abstract class StoreCiTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val diffJsonFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val diffSummaryMarkdownFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val diffHtmlFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    public abstract val validationReportFile: RegularFileProperty

    @get:Input
    public abstract val failOnDrift: Property<Boolean>

    @get:Input
    public abstract val failOnValidationErrors: Property<Boolean>

    @get:Input
    public abstract val postPrComment: Property<Boolean>

    @get:Input
    public abstract val commentMarker: Property<String>

    @get:Input
    @get:Optional
    public abstract val prNumber: Property<String>

    @get:Internal
    public abstract val githubToken: Property<String>

    @get:Internal
    public abstract val gitlabToken: Property<String>

    init {
        group = "store studio"
        description = "Universal CI/CD quality gate enforcing visual baselines and PR summaries."
        failOnDrift.convention(false)
        failOnValidationErrors.convention(true)
        postPrComment.convention(false)
        commentMarker.convention("<!-- STORE_STUDIO_DIFF_COMMENT -->")
    }

    @Option(
        option = "fail-on-drift",
        description = "Fail the CI quality gate if any visual changes / drift are detected.",
    )
    public fun setFailOnDriftOption(fail: Boolean) {
        failOnDrift.set(fail)
    }

    @Option(
        option = "fail-on-validation",
        description = "Fail the CI quality gate if store compliance violations are found.",
    )
    public fun setFailOnValidationOption(fail: Boolean) {
        failOnValidationErrors.set(fail)
    }

    @Option(
        option = "post-pr-comment",
        description = "Post or update sticky PR comment with visual diff summary.",
    )
    public fun setPostPrCommentOption(post: Boolean) {
        postPrComment.set(post)
    }

    @Option(
        option = "pr-number",
        description = "Explicit pull request or merge request number.",
    )
    public fun setPrNumberOption(pr: String) {
        prNumber.set(pr)
    }

    @TaskAction
    public fun executeQualityGate() {
        val valFile = validationReportFile.get().asFile
        val valReport = if (valFile.exists()) {
            ValidationReport.fromFile(valFile)
        } else {
            ValidationReport(valid = true)
        }

        val errors = valReport.violations.filter { it.severity == Severity.ERROR }
        val warnings = valReport.violations.filter { it.severity == Severity.WARNING }

        val diffFile = diffJsonFile.get().asFile
        if (!diffFile.exists()) {
            throw GradleException(
                "Store Studio: Diff JSON not found at '${diffFile.path}'. " +
                    "Ensure storeDiff completed successfully.",
            )
        }

        val diffDoc = DiffDocument.fromFile(diffFile)
        val s = diffDoc.summary
        val totalChanges =
            s.added + s.removed + s.modified + s.reordered + s.replaced + s.movedAndModified

        printConsoleSummary(errors, warnings, diffDoc, totalChanges)
        appendGitHubStepSummary()

        if (postPrComment.get()) {
            val md = diffSummaryMarkdownFile.get().asFile.readText()
            postPullRequestComment(md)
        }

        if (errors.isNotEmpty() && failOnValidationErrors.get()) {
            throw GradleException(
                "Store Studio CI Quality Gate FAILED: ${errors.size} store compliance error(s) " +
                    "found. Review report at: ${valFile.path}",
            )
        }

        if (totalChanges > 0 && failOnDrift.get()) {
            throw GradleException(
                "Store Studio CI Quality Gate FAILED: $totalChanges visual difference(s) " +
                    "detected against '${diffDoc.base.ref}'. " +
                    "Review report at: ${diffHtmlFile.get().asFile.path} or run " +
                    "'./gradlew storeRecordBaseline' to approve changes.",
            )
        }
    }

    private fun printConsoleSummary(
        errors: List<Any>,
        warnings: List<Any>,
        diffDoc: DiffDocument,
        totalChanges: Int,
    ) {
        val s = diffDoc.summary
        logger.lifecycle("")
        logger.lifecycle(
            "================================================================================",
        )
        logger.lifecycle("🏪 Chameleon Store Studio — CI Quality Gate")
        logger.lifecycle(
            "================================================================================",
        )
        val valStatus = if (errors.isEmpty()) {
            "PASSED (${warnings.size} warnings)"
        } else {
            "FAILED (${errors.size} errors, ${warnings.size} warnings)"
        }
        logger.lifecycle("Compliance Validation: $valStatus")
        val diffStatus = if (totalChanges == 0) {
            "PASSED (No visual differences detected)"
        } else {
            "DRIFT DETECTED ($totalChanges change(s) vs '${diffDoc.base.ref}')"
        }
        logger.lifecycle("Visual Diff Status:    $diffStatus")
        if (totalChanges > 0) {
            logger.lifecycle("  • Modified: ${s.modified}")
            logger.lifecycle("  • Added: ${s.added}")
            logger.lifecycle("  • Removed: ${s.removed}")
            logger.lifecycle("  • Reordered: ${s.reordered}")
            logger.lifecycle("  • Unchanged: ${s.unchanged}")
        }
        logger.lifecycle("Reports:")
        logger.lifecycle("  • Diff HTML:   ${diffHtmlFile.get().asFile.path}")
        logger.lifecycle("  • PR Summary:  ${diffSummaryMarkdownFile.get().asFile.path}")
    }

    private fun appendGitHubStepSummary() {
        val stepSummaryPath = System.getenv("GITHUB_STEP_SUMMARY")
        if (!stepSummaryPath.isNullOrBlank()) {
            val summaryFile = File(stepSummaryPath)
            if (summaryFile.exists() || summaryFile.parentFile?.exists() == true) {
                val mdContent = diffSummaryMarkdownFile.get().asFile.readText()
                summaryFile.appendText("\n" + mdContent + "\n")
                logger.lifecycle("GitHub Step Summary:   Appended diff summary to $stepSummaryPath")
            }
        }
    }

    private fun postPullRequestComment(markdownBody: String) {
        val target = resolvePrTarget()
        if (target == null) {
            logger.lifecycle(
                "Store Studio CI: PR commenting skipped — No CI PR environment detected.",
            )
            return
        }

        try {
            PrCommentClient.create().use { client ->
                runBlocking {
                    val response = client.postOrUpdateComment(target, markdownBody)
                    response.suspendOnSuccess {
                        logger.lifecycle(
                            "PR Comment:            ${data.action} on PR #${target.prNumber} " +
                                "(${data.url ?: "OK"})",
                        )
                    }.suspendOnError {
                        logger.warn(
                            "Store Studio CI: Failed to post PR comment (HTTP ${statusCode.code})",
                        )
                    }.suspendOnException {
                        logger.warn(
                            "Store Studio CI: Exception posting PR comment: ${throwable.message}",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Store Studio CI: Error executing PR comment client: ${e.message}")
        }
    }

    private fun resolvePrTarget(): PrCommentTarget? {
        val ghRepo = System.getenv("GITHUB_REPOSITORY")
        val ghToken = githubToken.orNull ?: System.getenv("GITHUB_TOKEN")
        if (!ghRepo.isNullOrBlank() && !ghToken.isNullOrBlank()) {
            val pr = resolveGitHubPrNumber()
            if (pr != null) {
                return PrCommentTarget(
                    platform = CiPlatform.GITHUB,
                    token = ghToken,
                    repoOrProjectId = ghRepo,
                    prNumber = pr,
                    commentMarker = commentMarker.get(),
                )
            }
        }

        val glProjectId = System.getenv("CI_PROJECT_ID")
        val glToken = gitlabToken.orNull ?: System.getenv("GITLAB_TOKEN")
            ?: System.getenv("CI_JOB_TOKEN")
        val glMrIid = prNumber.orNull ?: System.getenv("CI_MERGE_REQUEST_IID")
        if (!glProjectId.isNullOrBlank() && !glToken.isNullOrBlank() && !glMrIid.isNullOrBlank()) {
            val iid = glMrIid.toIntOrNull()
            if (iid != null) {
                return PrCommentTarget(
                    platform = CiPlatform.GITLAB,
                    token = glToken,
                    repoOrProjectId = glProjectId,
                    prNumber = iid,
                    commentMarker = commentMarker.get(),
                    baseUrl = System.getenv("CI_API_V4_URL"),
                )
            }
        }

        return null
    }

    private fun resolveGitHubPrNumber(): Int? {
        prNumber.orNull?.toIntOrNull()?.let { return it }

        val ref = System.getenv("GITHUB_REF") ?: ""
        val match = Regex("""refs/pull/(\d+)/""").find(ref)
        if (match != null) {
            return match.groupValues[1].toIntOrNull()
        }

        val eventPath = System.getenv("GITHUB_EVENT_PATH")
        if (!eventPath.isNullOrBlank()) {
            val eventFile = File(eventPath)
            if (eventFile.exists()) {
                try {
                    val root = Json.parseToJsonElement(eventFile.readText()).jsonObject
                    val prObj = root["pull_request"]?.jsonObject
                    val numberPrimitive = prObj?.get("number")?.jsonPrimitive
                        ?: root["number"]?.jsonPrimitive
                    return numberPrimitive?.content?.toIntOrNull()
                } catch (ignored: Exception) {
                }
            }
        }

        return null
    }
}
