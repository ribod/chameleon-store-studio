package dev.ribod.storestudio.idea

import dev.ribod.storestudio.idea.io.StoreArtifactReader
import dev.ribod.storestudio.idea.io.childWithin
import dev.ribod.storestudio.idea.model.ExportAssetDto
import dev.ribod.storestudio.idea.model.ExportIndexDto
import dev.ribod.storestudio.idea.model.ManifestEntryDto
import dev.ribod.storestudio.idea.model.MergedManifestDto
import dev.ribod.storestudio.idea.model.ModuleStoreArtifacts
import dev.ribod.storestudio.idea.model.SourceLocationDto
import dev.ribod.storestudio.idea.model.ValidationReportDto
import dev.ribod.storestudio.idea.model.ViolationDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StoreArtifactReaderTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testChildWithinKeepsPathsInsideBaseDir() {
        val base = tempFolder.newFolder("base")
        File(base, "sub").mkdirs()
        assertEquals(File(base, "sub/a.png"), base.childWithin("sub/a.png"))
        assertNull(base.childWithin("../escape.png"))
        assertNull(base.childWithin("sub/../../escape.png"))
        assertNull(base.childWithin(File(tempFolder.root, "outside.png").absolutePath))
    }

    @Test
    fun testComputeAssetCardsFromExport() {
        val root = tempFolder.newFolder("module")
        val buildStore = File(root, "build/store").apply { mkdirs() }
        val exportDir = File(buildStore, "export/play_store/en-US/phone").apply { mkdirs() }
        val imageFile = File(exportDir, "01_agenda.png").apply { writeText("fake-png-content") }

        val exportIndex = ExportIndexDto(
            schemaVersion = 1,
            assets = listOf(
                ExportAssetDto(
                    slotKey = "PLAY_STORE/PHONE/en-US/agenda",
                    target = "PLAY_STORE",
                    deviceSlot = "PHONE",
                    storeLocale = "en-US",
                    id = "agenda",
                    order = 1,
                    file = "play_store/en-US/phone/01_agenda.png",
                    widthPx = 1080,
                    heightPx = 1920,
                    engine = "ROBOLECTRIC",
                ),
            ),
        )

        val manifest = MergedManifestDto(
            schemaVersion = 1,
            module = ":app",
            entries = listOf(
                ManifestEntryDto(
                    id = "agenda",
                    order = 1,
                    title = "Conference Schedule",
                    functionFqn = "com.example.AppKt.AgendaScreen",
                    functionName = "AgendaScreen",
                    source = SourceLocationDto("App.kt", 10),
                ),
            ),
        )

        val validation = ValidationReportDto(
            schemaVersion = 1,
            valid = false,
            violations = listOf(
                ViolationDto(
                    ruleId = "TEST_WARN",
                    severity = "WARNING",
                    slotKey = "PLAY_STORE/PHONE/en-US/agenda",
                    message = "Test warning",
                ),
            ),
        )

        val moduleArtifacts = ModuleStoreArtifacts(
            moduleName = ":app",
            moduleDir = root,
            buildStoreDir = buildStore,
            manifest = manifest,
            exportIndex = exportIndex,
            validationReport = validation,
        )

        val cards = StoreArtifactReader.computeAssetCards(listOf(moduleArtifacts))
        assertEquals(1, cards.size)

        val card = cards[0]
        assertEquals(":app", card.moduleName)
        assertEquals("agenda", card.id)
        assertEquals("Conference Schedule", card.title)
        assertEquals("ROBOLECTRIC", card.engine)
        assertEquals("com.example.AppKt.AgendaScreen", card.functionFqn)
        assertEquals("AgendaScreen", card.functionName)
        assertEquals(1, card.violations.size)
        assertEquals("TEST_WARN", card.violations[0].ruleId)
        assertEquals("App.kt", card.sourceLocation?.path)
    }

    @Test
    fun testFallbackRenderReplacesTheFailedLayoutlibJob() {
        val storeDir = File(tempFolder.newFolder("app"), "build/store")
        File(storeDir, "render/raw").mkdirs()
        val fallbackImage = File(storeDir, "render/robolectric/raw/home.png").apply {
            parentFile.mkdirs()
            writeText("png")
        }
        fun job(status: String, engine: String, image: String?) = """
            {"jobId": "home", "slotKey": "PLAY_STORE/PHONE/en-US/home", "entryId": "home",
             "target": "PLAY_STORE", "deviceSlot": "PHONE", "storeLocale": "en-US",
             "widthPx": 1080, "heightPx": 1920, "engine": "$engine", "status": "$status",
             "image": ${image?.let { "\"$it\"" } ?: "null"}}
        """
        File(storeDir, "render/index.json").writeText(
            """{"schemaVersion": 1, "jobs": [${job("FAILED", "LAYOUTLIB", null)}]}""",
        )
        File(storeDir, "render/robolectric/index.json").writeText(
            """{"schemaVersion": 1, "jobs": [${job("SUCCESS", "ROBOLECTRIC", "home.png")}]}""",
        )

        val artifacts = StoreArtifactReader.loadModuleArtifacts(":app", storeDir)!!
        val cards = StoreArtifactReader.computeAssetCards(listOf(artifacts))

        assertEquals(1, cards.size)
        assertEquals("ROBOLECTRIC", cards.single().engine)
        assertEquals(fallbackImage, cards.single().imageFile)
    }
}
