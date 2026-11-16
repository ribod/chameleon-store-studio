package dev.ribod.storestudio.idea.navigation

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * Kotlin implementation of [StoreSourceResolver], registered only when the Kotlin plugin is
 * installed (see `storestudio-kotlin.xml`).
 */
internal class KotlinStoreSourceResolver : StoreSourceResolver {

    override fun findInFile(
        project: Project,
        file: VirtualFile,
        functionName: String?,
        functionFqn: String?,
        annotationId: String?,
    ): Navigatable? {
        val ktFile = PsiManager.getInstance(project).findFile(file) as? KtFile ?: return null

        // @StoreScreenshot functions are top-level (enforced by KSP); top-level declarations are
        // served from stubs, so the full AST is only loaded when that fast path finds nothing.
        val topLevel = ktFile.declarations.filterIsInstance<KtNamedFunction>()
        return match(topLevel, functionName, functionFqn, annotationId)
            ?: match(
                PsiTreeUtil.findChildrenOfType(ktFile, KtNamedFunction::class.java),
                functionName,
                functionFqn,
                annotationId,
            )
    }

    override fun findInProject(
        project: Project,
        functionName: String?,
        functionFqn: String?,
        annotationId: String?,
    ): Navigatable? {
        val word = functionName?.takeIf { it.isNotBlank() }
            ?: functionFqn?.takeIf { it.isNotBlank() }?.let(::shortName)
            ?: annotationId?.takeIf { it.isNotBlank() }
            ?: return null

        // The word index narrows the search to files that mention the identifier at all
        var result: Navigatable? = null
        PsiSearchHelper.getInstance(project).processAllFilesWithWord(
            word,
            GlobalSearchScope.projectScope(project),
            { psiFile ->
                val virtualFile = psiFile.virtualFile
                if (psiFile is KtFile && virtualFile != null) {
                    result =
                        findInFile(project, virtualFile, functionName, functionFqn, annotationId)
                }
                result == null
            },
            true,
        )
        return result
    }

    private fun match(
        functions: Collection<KtNamedFunction>,
        functionName: String?,
        fqn: String?,
        annotationId: String?,
    ): Navigatable? {
        if (!functionName.isNullOrBlank()) {
            functions.firstOrNull { it.name == functionName }?.let { return it }
        }

        if (!fqn.isNullOrBlank()) {
            val shortName = shortName(fqn)
            functions.firstOrNull { it.fqName?.asString() == fqn || it.name == shortName }
                ?.let { return it }
        }

        if (!annotationId.isNullOrBlank()) {
            functions.firstOrNull { fn ->
                fn.annotationEntries.any { entry ->
                    val text = entry.text
                    text.contains("StoreScreenshot") && text.contains(annotationId)
                }
            }?.let { return it }
        }

        return null
    }

    private fun shortName(fqn: String): String = fqn.substringAfterLast('.').substringAfterLast('$')
}
