package dev.ribod.storestudio.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

/**
 * Service provider interface for [StoreStudioSymbolProcessor].
 *
 * Registered in `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`
 * to enable compile-time symbol discovery in KSP 2.x.
 *
 * Extracts Gradle configuration arguments:
 * - `storeStudio.modulePath`: Gradle module path (e.g. `":feature:onboarding"`).
 * - `storeStudio.projectRootDir`: Absolute path to project root directory for path relativization.
 */
public class StoreStudioSymbolProcessorProvider : SymbolProcessorProvider {

    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        val modulePath = environment.options[OPTION_MODULE_PATH] ?: DEFAULT_MODULE_PATH
        val projectRootDir = environment.options[OPTION_PROJECT_ROOT_DIR] ?: DEFAULT_ROOT_DIR

        return StoreStudioSymbolProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            modulePath = modulePath,
            projectRootDir = projectRootDir,
        )
    }

    public companion object {
        public const val OPTION_MODULE_PATH: String = "storeStudio.modulePath"
        public const val OPTION_PROJECT_ROOT_DIR: String = "storeStudio.projectRootDir"
        public const val DEFAULT_MODULE_PATH: String = ":app"
        public const val DEFAULT_ROOT_DIR: String = "."
    }
}
