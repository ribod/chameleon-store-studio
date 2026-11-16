package dev.ribod.storestudio.idea

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ribod.storestudio.idea.navigation.StoreSourceResolver
import org.jetbrains.kotlin.psi.KtNamedFunction

class KotlinStoreSourceResolverTest : BasePlatformTestCase() {

    private val source = """
        package com.example.screens

        annotation class StoreScreenshot(val id: String)

        @StoreScreenshot(id = "agenda_shot")
        fun AgendaScreenshot() {
            fun helper() {}
        }

        @StoreScreenshot("speakers_shot")
        fun SpeakersScreenshot() {}
    """.trimIndent()

    private val resolver: StoreSourceResolver
        get() = StoreSourceResolver.EP_NAME.extensionList.single()

    fun testResolverIsRegisteredThroughOptionalKotlinDescriptor() {
        assertEquals("KotlinStoreSourceResolver", resolver.javaClass.simpleName)
    }

    fun testFindsFunctionInFileByNameFqnAndAnnotationId() {
        val file = myFixture.configureByText("screens.kt", source).virtualFile

        fun nameOf(functionName: String?, fqn: String?, id: String?): String? =
            (resolver.findInFile(project, file, functionName, fqn, id) as? KtNamedFunction)?.name

        assertEquals("AgendaScreenshot", nameOf("AgendaScreenshot", null, null))
        assertEquals(
            "SpeakersScreenshot",
            nameOf(null, "com.example.screens.ScreensKt.SpeakersScreenshot", null),
        )
        assertEquals("SpeakersScreenshot", nameOf(null, null, "speakers_shot"))
        assertEquals("helper", nameOf("helper", null, null))
        assertNull(nameOf("Missing", null, null))
    }

    fun testFindsFunctionAcrossProjectUsingWordIndex() {
        myFixture.addFileToProject("other/Unrelated.kt", "package other\n\nfun unrelated() {}\n")
        myFixture.addFileToProject("com/example/screens/screens.kt", source)

        val found = resolver.findInProject(project, "SpeakersScreenshot", null, null)
        assertEquals("SpeakersScreenshot", (found as? KtNamedFunction)?.name)
        assertNull(resolver.findInProject(project, "DoesNotExist", null, null))
    }
}
