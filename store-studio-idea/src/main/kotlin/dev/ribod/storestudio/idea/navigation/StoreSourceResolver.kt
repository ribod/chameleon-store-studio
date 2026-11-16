package dev.ribod.storestudio.idea.navigation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable

/**
 * Resolves `@StoreScreenshot` composables to navigatable source elements.
 *
 * Language support is contributed through the `dev.ribod.storestudio.idea.sourceResolver`
 * extension point, so the Kotlin implementation is only loaded when the Kotlin plugin is present.
 */
public interface StoreSourceResolver {

    /**
     * Finds the composable inside [file]. Called inside a read action; must not rely on indexes
     * so it also works while the IDE is indexing.
     */
    public fun findInFile(
        project: Project,
        file: VirtualFile,
        functionName: String?,
        functionFqn: String?,
        annotationId: String?,
    ): Navigatable?

    /** Finds the composable anywhere in the project. Called inside a read action in smart mode. */
    public fun findInProject(
        project: Project,
        functionName: String?,
        functionFqn: String?,
        annotationId: String?,
    ): Navigatable?

    public companion object {
        public val EP_NAME: ExtensionPointName<StoreSourceResolver> =
            ExtensionPointName.create("dev.ribod.storestudio.idea.sourceResolver")
    }
}
