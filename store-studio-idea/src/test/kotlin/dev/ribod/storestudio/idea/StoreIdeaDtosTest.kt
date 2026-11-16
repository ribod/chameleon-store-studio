package dev.ribod.storestudio.idea

import com.google.gson.Gson
import dev.ribod.storestudio.idea.model.DiffDocumentDto
import dev.ribod.storestudio.idea.model.ExportIndexDto
import dev.ribod.storestudio.idea.model.MergedManifestDto
import dev.ribod.storestudio.idea.model.RenderIndexDto
import dev.ribod.storestudio.idea.model.StoreMetadataManifestDto
import dev.ribod.storestudio.idea.model.ValidationReportDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreIdeaDtosTest {

    private val gson = Gson()

    @Test
    fun testParseMergedManifest() {
        val json = """
            {
                "schemaVersion": 1,
                "module": ":app",
                "entries": [
                    {
                        "kind": "SCREENSHOT",
                        "id": "screen_agenda",
                        "order": 1,
                        "title": "Conference Schedule",
                        "functionFqn": "com.example.AppKt.Agenda",
                        "devices": ["PHONE", "TABLET"],
                        "targets": ["PLAY_STORE"],
                        "source": { "path": "app/src/main/App.kt", "line": 42 }
                    }
                ]
            }
        """.trimIndent()

        val manifest = gson.fromJson(json, MergedManifestDto::class.java)
        assertEquals(1, manifest.schemaVersion)
        assertEquals(":app", manifest.module)
        assertEquals(1, manifest.entries.size)

        val entry = manifest.entries[0]
        assertEquals("screen_agenda", entry.id)
        assertEquals(1, entry.order)
        assertEquals("Conference Schedule", entry.title)
        assertEquals(42, entry.source?.line)
    }

    @Test
    fun testParseRenderIndex() {
        val json = """
            {
                "schemaVersion": 1,
                "engineDefault": "LAYOUTLIB",
                "jobs": [
                    {
                        "jobId": "job1",
                        "slotKey": "PLAY_STORE/PHONE/en-US/agenda",
                        "entryId": "agenda",
                        "target": "PLAY_STORE",
                        "deviceSlot": "PHONE",
                        "storeLocale": "en-US",
                        "widthPx": 1080,
                        "heightPx": 1920,
                        "engine": "ROBOLECTRIC",
                        "status": "SUCCESS",
                        "image": "agenda.png",
                        "diagnostics": [
                            { "severity": "WARNING", "code": "LAYOUTLIB_RENDER_FAILED", "message": "Render problem" }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val index = gson.fromJson(json, RenderIndexDto::class.java)
        assertEquals(1, index.jobs.size)
        val job = index.jobs[0]
        assertEquals("agenda", job.entryId)
        assertEquals("ROBOLECTRIC", job.engine)
        assertEquals("SUCCESS", job.status)
        assertEquals(1080, job.widthPx)
        assertEquals("LAYOUTLIB_RENDER_FAILED", job.diagnostics.single().code)
    }

    @Test
    fun testParseExportIndex() {
        val json = """
            {
                "schemaVersion": 1,
                "assets": [
                    {
                        "slotKey": "PLAY_STORE/PHONE/en-US/agenda",
                        "target": "PLAY_STORE",
                        "deviceSlot": "PHONE",
                        "storeLocale": "en-US",
                        "id": "agenda",
                        "order": 1,
                        "file": "play_store/en-US/phone/01_agenda.png",
                        "widthPx": 1080,
                        "heightPx": 1920,
                        "engine": "LAYOUTLIB"
                    }
                ]
            }
        """.trimIndent()

        val export = gson.fromJson(json, ExportIndexDto::class.java)
        assertEquals(1, export.assets.size)
        assertEquals("agenda", export.assets[0].id)
        assertEquals(1, export.assets[0].order)
    }

    @Test
    fun testParseValidationReport() {
        val json = """
            {
                "schemaVersion": 1,
                "valid": false,
                "violations": [
                    {
                        "ruleId": "ASPECT_RATIO_MISMATCH",
                        "severity": "WARNING",
                        "target": "PLAY_STORE",
                        "message": "Dimensions do not match target aspect ratio",
                        "hint": "Check preview annotation"
                    }
                ]
            }
        """.trimIndent()

        val report = gson.fromJson(json, ValidationReportDto::class.java)
        assertEquals(false, report.valid)
        assertEquals(1, report.violations.size)
        assertEquals("WARNING", report.violations[0].severity)
        assertEquals("ASPECT_RATIO_MISMATCH", report.violations[0].ruleId)
    }

    @Test
    fun testParseDiffDocument() {
        val json = """
            {
                "schemaVersion": 1,
                "summary": {
                    "modified": 1,
                    "added": 0,
                    "removed": 0
                },
                "changes": [
                    {
                        "type": "MODIFIED",
                        "bucket": "PLAY_STORE/PHONE/en-US",
                        "id": "agenda",
                        "pHashDistance": 0.08,
                        "hasVisualOverflow": true,
                        "removedTexts": ["Old Title"],
                        "addedTexts": ["New Title"]
                    }
                ]
            }
        """.trimIndent()

        val diff = gson.fromJson(json, DiffDocumentDto::class.java)
        assertEquals(1, diff.summary.modified)
        assertEquals(1, diff.changes.size)
        val change = diff.changes[0]
        assertEquals("MODIFIED", change.type)
        assertTrue(change.hasVisualOverflow)
        assertEquals(listOf("Old Title"), change.removedTexts)
        assertEquals(listOf("New Title"), change.addedTexts)
    }

    @Test
    fun testParseStoreMetadataManifest() {
        val json = """
            {
                "schemaVersion": 1,
                "defaultLocale": "en-US",
                "locales": {
                    "en-US": {
                        "locale": "en-US",
                        "play": {
                            "title": "KotlinConf",
                            "shortDescription": "Conference App"
                        },
                        "appStore": {
                            "name": "KotlinConf iOS",
                            "keywords": "kotlin,conference"
                        }
                    }
                }
            }
        """.trimIndent()

        val meta = gson.fromJson(json, StoreMetadataManifestDto::class.java)
        assertEquals("en-US", meta.defaultLocale)
        val bundle = meta.locales["en-US"]
        assertNotNull(bundle)
        assertEquals("KotlinConf", bundle!!.play.title)
        assertEquals("Conference App", bundle.play.shortDescription)
        assertEquals("KotlinConf iOS", bundle.appStore.name)
        assertEquals("kotlin,conference", bundle.appStore.keywords)
    }
}
