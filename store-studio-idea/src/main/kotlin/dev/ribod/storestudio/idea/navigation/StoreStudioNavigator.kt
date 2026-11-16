package dev.ribod.storestudio.idea.navigation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import dev.ribod.storestudio.idea.model.SourceLocationDto
import java.io.File

/**
 * Handles fast and resilient editor navigation from screenshot cards to Kotlin source code.
 *
 * Employs a multi-tier resolution strategy:
 * 1. Fast path: Direct file path + line number captured during build/KSP. Works during indexing.
 * 2. Secondary path: File search by basename in project scope (after indexing).
 * 3. Exact element: Resolves the composable function element via [StoreSourceResolver].
 * 4. Smart fallback: Project-wide lookup by function name, FQN or annotation ID.
 *
 * All PSI and index access runs in cancellable non-blocking read actions off the EDT.
 */
public object StoreStudioNavigator {

    private class Target(val file: VirtualFile?, val element: Navigatable?)

    /**
     * Navigates to the source code of a screenshot composable.
     */
    public fun navigate(
        project: Project,
        sourceLocation: SourceLocationDto?,
        functionFqn: String? = null,
        functionName: String? = null,
        annotationId: String? = null,
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed) return@executeOnPooledThread
            val relOrAbsPath = sourceLocation?.path?.takeIf { it.isNotBlank() }

            // VFS refresh must happen outside of read actions
            val directFile = relOrAbsPath?.let { findLocalFile(project, it) }

            val readAction = ReadAction.nonBlocking<Target> {
                val file = directFile ?: relOrAbsPath?.let { findByFileName(project, it) }
                val element = if (file != null) {
                    resolvers().firstNotNullOfOrNull {
                        it.findInFile(project, file, functionName, functionFqn, annotationId)
                    }
                } else {
                    resolvers().firstNotNullOfOrNull {
                        it.findInProject(project, functionName, functionFqn, annotationId)
                    }
                }
                Target(file, element)
            }
            // Index lookups are only needed when the recorded path could not be resolved
            val pipeline = if (directFile != null) readAction else readAction.inSmartMode(project)

            pipeline
                .expireWhen { project.isDisposed }
                .coalesceBy(project, StoreStudioNavigator)
                .finishOnUiThread(ModalityState.defaultModalityState()) { target ->
                    val element = target.element
                    if (element != null && element.canNavigate()) {
                        element.navigate(true)
                    } else if (target.file != null) {
                        val line = ((sourceLocation?.line ?: 1) - 1).coerceAtLeast(0)
                        OpenFileDescriptor(project, target.file, line, 0).navigate(true)
                    }
                }
                .submit(AppExecutorUtil.getAppExecutorService())
        }
    }

    /**
     * Navigates directly to an arbitrary file (e.g. metadata text file or report)
     * at a specific line.
     */
    public fun navigateToFile(project: Project, file: File, line: Int = 1) {
        ApplicationManager.getApplication().executeOnPooledThread {
            if (project.isDisposed || !file.exists()) return@executeOnPooledThread

            val lfs = LocalFileSystem.getInstance()
            val virtualFile = lfs.findFileByPath(file.absolutePath)
                ?: lfs.refreshAndFindFileByPath(file.absolutePath)
                ?: return@executeOnPooledThread

            ApplicationManager.getApplication().invokeLater(
                {
                    if (virtualFile.isValid) {
                        OpenFileDescriptor(
                            project,
                            virtualFile,
                            (line - 1).coerceAtLeast(0),
                            0,
                        ).navigate(true)
                    }
                },
                ModalityState.defaultModalityState(),
                project.disposed,
            )
        }
    }

    private fun resolvers(): List<StoreSourceResolver> = StoreSourceResolver.EP_NAME.extensionList

    private fun findLocalFile(project: Project, relOrAbsPath: String): VirtualFile? {
        val candidate = if (File(relOrAbsPath).isAbsolute) {
            File(relOrAbsPath)
        } else {
            File(project.basePath ?: "", relOrAbsPath)
        }
        val lfs = LocalFileSystem.getInstance()
        return (
            lfs.findFileByPath(candidate.absolutePath)
                ?: lfs.refreshAndFindFileByPath(candidate.absolutePath)
            )?.takeIf { it.isValid }
    }

    private fun findByFileName(project: Project, relOrAbsPath: String): VirtualFile? {
        val matches = FilenameIndex.getVirtualFilesByName(
            File(relOrAbsPath).name,
            GlobalSearchScope.projectScope(project),
        )
        return matches.firstOrNull { it.path.endsWith(relOrAbsPath) }
            ?: matches.firstOrNull { it.isValid }
    }
}
