package dev.ribod.storestudio.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies that manifest merging only depends on the KSP pass of the configured variant, in this
 * module and in the modules it depends on, and that the processor is not added to other passes.
 */
class KspWiringTest {

    private fun androidModule(name: String, parent: Project): Project {
        val module = ProjectBuilder.builder().withName(name).withParent(parent).build()
        module.pluginManager.apply("com.android.application")
        module.pluginManager.apply("dev.ribod.storestudio")
        return module
    }

    private fun Task.dependencyPaths(): Set<String> =
        taskDependencies.getDependencies(this).map { it.path }.toSet()

    @Test
    fun mergeManifestDependsOnlyOnTheConfiguredVariantKspTasks() {
        val root = ProjectBuilder.builder().build()
        val feature = androidModule("feature", root)
        val app = androidModule("app", root)
        app.dependencies.add(
            "implementation",
            app.dependencies.project(mapOf("path" to ":feature")),
        )

        (feature as ProjectInternal).evaluate()
        (app as ProjectInternal).evaluate()

        val mergeDependencies = app.tasks.getByName("storeMergeManifest").dependencyPaths()
        assertEquals(setOf(":app:kspDebugKotlin", ":feature:kspDebugKotlin"), mergeDependencies)
    }

    @Test
    fun processorIsAddedOnceToTheSharedKspConfiguration() {
        val root = ProjectBuilder.builder().build()
        val app = androidModule("app", root)
        (app as ProjectInternal).evaluate()

        // With AGP's built-in Kotlin only 'ksp' reaches the variant processor classpaths
        val withProcessor = app.configurations
            .filter { it.name.startsWith("ksp") }
            .filter { config -> config.dependencies.any { it.name == "store-studio-ksp" } }
            .map { it.name }
        assertEquals(listOf("ksp"), withProcessor)
    }

    @Test
    fun processorHonorsAutoDependenciesSetAfterApply() {
        val root = ProjectBuilder.builder().build()
        val app = androidModule("app", root)
        app.extensions.getByType(dev.ribod.storestudio.gradle.dsl.StoreStudioExtension::class.java)
            .autoDependencies.set(false)
        (app as ProjectInternal).evaluate()

        assertEquals(
            emptyList<String>(),
            app.configurations.getByName("ksp").dependencies.map { it.name },
        )
    }
}
