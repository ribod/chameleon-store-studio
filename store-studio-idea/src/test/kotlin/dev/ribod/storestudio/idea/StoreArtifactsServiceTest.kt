package dev.ribod.storestudio.idea

import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import dev.ribod.storestudio.idea.io.StoreArtifactsService
import java.io.File

class StoreArtifactsServiceTest : BasePlatformTestCase() {

    fun testReloadPublishesSnapshotWithDiscoveredModulesAndCards() {
        val basePath = requireNotNull(project.basePath)
        val storeDir = File(basePath, "app/build/store").apply { mkdirs() }
        File(storeDir, "export/play/en-US").mkdirs()
        File(storeDir, "export/play/en-US/01_home.png").writeText("png")
        File(storeDir, "export/index.json").writeText(
            """
            {"schemaVersion":1,"assets":[{"slotKey":"PLAY_STORE/PHONE/en-US/home",
            "target":"PLAY_STORE","deviceSlot":"PHONE","storeLocale":"en-US","id":"home",
            "order":1,"file":"play/en-US/01_home.png","widthPx":1080,"heightPx":1920}]}
            """.trimIndent(),
        )

        try {
            val service = StoreArtifactsService.getInstance(project)
            service.requestReload()

            val deadline = System.currentTimeMillis() + 15_000
            var snapshot: StoreArtifactsService.Snapshot? = null
            while (snapshot == null) {
                assertTrue(
                    "No snapshot, last: ${service.latest?.modules?.map { it.moduleName }}",
                    System.currentTimeMillis() < deadline,
                )
                PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
                snapshot =
                    service.latest?.takeIf { s ->
                        s.modules.any { it.moduleName == ":app" }
                    }
                Thread.sleep(20)
            }
            requireNotNull(snapshot)
            assertEquals(listOf("home"), snapshot.cards.map { it.id })
        } finally {
            File(basePath, "app").deleteRecursively()
        }
    }
}
