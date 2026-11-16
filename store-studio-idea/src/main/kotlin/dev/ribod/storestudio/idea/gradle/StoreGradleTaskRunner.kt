package dev.ribod.storestudio.idea.gradle

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import dev.ribod.storestudio.idea.io.StoreArtifactsService

/**
 * Executes Store Studio Gradle tasks asynchronously using IntelliJ's External System integration.
 */
public object StoreGradleTaskRunner {

    private val GRADLE_SYSTEM_ID = ProjectSystemId("GRADLE")
    private const val NOTIFICATION_GROUP = "Compose Store Studio"

    /**
     * Executes a Gradle task in the specified project or module directory.
     *
     * @param project The active IntelliJ project
     * @param modulePath Root directory of the Gradle project/module to execute in
     * @param taskName Name of the task (e.g. "storeExport", "storeValidate", "storeDiff")
     * @param taskOptions Options of [taskName] (e.g. listOf("--publish-dry-run")); they are placed
     *   right after the task name so Gradle binds them to the task, not to the build.
     * @param arguments Extra Gradle CLI arguments (e.g. listOf("-Pkey=value"))
     */
    public fun runTask(
        project: Project,
        modulePath: String,
        taskName: String,
        taskOptions: List<String> = emptyList(),
        arguments: List<String> = emptyList(),
    ) = runTasks(project, modulePath, listOf(taskName) + taskOptions, arguments)

    /**
     * Regenerates what the Gallery and Store Preview show: the export (images) and the consolidated
     * metadata (texts and icons).
     */
    public fun regenerate(project: Project, modulePath: String) =
        runTasks(project, modulePath, listOf("storeExport", "storeParseMetadata"))

    /** Runs several Gradle tasks (and their options) in one invocation; see [runTask]. */
    public fun runTasks(
        project: Project,
        modulePath: String,
        taskNames: List<String>,
        arguments: List<String> = emptyList(),
    ) {
        val taskName = taskNames.first()
        val settings = ExternalSystemTaskExecutionSettings().apply {
            externalSystemIdString = GRADLE_SYSTEM_ID.id
            externalProjectPath = modulePath
            this.taskNames = taskNames
            if (arguments.isNotEmpty()) {
                scriptParameters = arguments.joinToString(" ")
            }
        }

        // Rescan as soon as the task finishes (also on failure: partial outputs are still useful),
        // independently of whether the VFS has noticed the new files yet.
        val reloadOnFinish = object : TaskCallback {
            override fun onSuccess() = reload(project)
            override fun onFailure() = reload(project)
        }

        try {
            ExternalSystemUtil.runTask(
                settings,
                DefaultRunExecutor.EXECUTOR_ID,
                project,
                GRADLE_SYSTEM_ID,
                reloadOnFinish,
                ProgressExecutionMode.IN_BACKGROUND_ASYNC,
                false,
            )
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                ?.createNotification(
                    "Store Studio: Failed to launch task",
                    "Could not execute $taskName: ${e.message}",
                    NotificationType.ERROR,
                )?.notify(project)
        }
    }

    private fun reload(project: Project) {
        if (!project.isDisposed) StoreArtifactsService.getInstance(project).requestReload()
    }
}
