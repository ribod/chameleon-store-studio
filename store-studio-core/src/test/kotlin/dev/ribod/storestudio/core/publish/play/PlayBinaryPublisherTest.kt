package dev.ribod.storestudio.core.publish.play

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.test.assertError
import com.skydoves.sandwich.test.assertSuccess
import com.skydoves.sandwich.test.fakeError
import com.skydoves.sandwich.test.fakeSuccess
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayBinaryPublisherTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private val api = FakeBinaryApi()
    private val publisher = PlayStorePublisher(api)
    private val creds = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}"

    private fun file(name: String) = File(tempDir.root, name).apply { writeBytes(ByteArray(8)) }

    private fun request(
        files: List<File> = listOf(file("app.aab")),
        block: (PlayBinaryPublishRequest) -> PlayBinaryPublishRequest = { it },
    ) = block(PlayBinaryPublishRequest("com.example.app", creds, files))

    private fun assertLocalError(req: PlayBinaryPublishRequest, fragment: String) = runBlocking {
        val response = publisher.publishBinary(req)
        response.assertError { }
        assertTrue((response as ApiResponse.Failure.Error).payload.toString().contains(fragment))
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun aabHappyPathCommitsAndBuildsTrackBody() = runBlocking {
        val req = request {
            it.copy(
                releaseName = "1.0",
                inAppUpdatePriority = 3,
                releaseNotesByLocale = mapOf("en-US" to "Fixes"),
            )
        }
        publisher.publishBinary(req).assertSuccess {
            assertEquals(PlayPublishStatus.COMMITTED, data.status)
            assertEquals(listOf(7L), data.versionCodes)
            assertEquals("1001", data.editId)
        }
        assertEquals(
            listOf("auth", "insert", "bundle:app.aab", "track:internal", "validate", "commit"),
            api.calls,
        )
        val release = api.trackBodies.single()["releases"]!!.jsonArray.single().jsonObject
        assertEquals("internal", api.trackBodies.single()["track"]!!.jsonPrimitive.content)
        assertEquals("1.0", release["name"]!!.jsonPrimitive.content)
        assertEquals("completed", release["status"]!!.jsonPrimitive.content)
        assertEquals("7", release["versionCodes"]!!.jsonArray.single().jsonPrimitive.content)
        assertEquals("3", release["inAppUpdatePriority"]!!.jsonPrimitive.content)
        val note = release["releaseNotes"]!!.jsonArray.single().jsonObject
        assertEquals("en-US", note["language"]!!.jsonPrimitive.content)
        assertEquals("Fixes", note["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun multipleFilesAndRetainedCodes() = runBlocking {
        api.nextCodes.addAll(listOf(11L, 12L))
        val req = request(listOf(file("a.aab"), file("b.aab"))) {
            it.copy(retainVersionCodes = listOf(5L, 11L))
        }
        publisher.publishBinary(req).assertSuccess {
            assertEquals(listOf(5L, 11L, 12L), data.versionCodes)
        }
        val release = api.trackBodies.single()["releases"]!!.jsonArray.single().jsonObject
        val codes = release["versionCodes"]!!
        assertEquals(listOf("5", "11", "12"), codes.jsonArray.map { it.jsonPrimitive.content })
    }

    @Test
    fun apkIsRejectedBeforeAnyApiCall() = runBlocking {
        assertLocalError(request(listOf(file("app.apk"))), "expected .aab")
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun mappingAndSymbolsUploadedPerVersionCode() = runBlocking {
        api.nextCodes.addAll(listOf(1L, 2L))
        val req = request(listOf(file("a.aab"), file("b.aab"))) {
            it.copy(mappingFile = file("mapping.txt"), debugSymbolsFile = file("sym.zip"))
        }
        publisher.publishBinary(req).assertSuccess { }
        val extras = api.calls.filter { it.startsWith("deob") }
        assertEquals(
            listOf("deob:1:proguard", "deob:1:nativeCode", "deob:2:proguard", "deob:2:nativeCode"),
            extras,
        )
    }

    @Test
    fun uploadFailureDeletesEdit() = runBlocking {
        api.bundleOverride = ApiResponse.fakeError(payload = "boom")
        publisher.publishBinary(request()).assertError { }
        assertEquals(listOf("auth", "insert", "bundle:app.aab", "delete"), api.calls)
    }

    @Test
    fun dryRunValidatedDeletesInsteadOfCommitting() = runBlocking {
        publisher.publishBinary(request { it.copy(dryRun = true) }).assertSuccess {
            assertEquals(PlayPublishStatus.DRY_RUN_VALIDATED, data.status)
        }
        assertTrue(api.calls.contains("delete"))
        assertTrue(api.calls.none { it == "commit" })
    }

    @Test
    fun dryRunWithoutCredentialsIsLocalAndOffline() = runBlocking {
        publisher.publishBinary(request { it.copy(dryRun = true, serviceAccountJson = null) })
            .assertSuccess {
                assertEquals(PlayPublishStatus.DRY_RUN_LOCAL, data.status)
                assertTrue(data.formattedReport.contains("app.aab"))
            }
        assertEquals(emptyList<String>(), api.calls)
    }

    @Test
    fun missingCredentialsFails() {
        assertLocalError(
            request {
                it.copy(serviceAccountJson = null)
            },
            "PLAY_SERVICE_ACCOUNT_JSON",
        )
    }

    @Test
    fun invalidFractionAndStatusCombosFailLocally() {
        assertLocalError(request { it.copy(userFraction = 0.5) }, "userFraction is only valid")
        val rollout = request { it.copy(status = PlayReleaseStatus.IN_PROGRESS) }
        assertLocalError(rollout, "requires userFraction")
        val high = request { it.copy(status = PlayReleaseStatus.IN_PROGRESS, userFraction = 1.0) }
        assertLocalError(high, "less than 1")
        val zero = request { it.copy(status = PlayReleaseStatus.HALTED, userFraction = 0.0) }
        assertLocalError(zero, "greater than 0")
        assertLocalError(request { it.copy(inAppUpdatePriority = 6) }, "between 0 and 5")
    }

    @Test
    fun invalidFilesFailLocally() {
        assertLocalError(request(emptyList()), "No release files")
        assertLocalError(request(listOf(File(tempDir.root, "missing.aab"))), "does not exist")
        assertLocalError(request(listOf(file("x.zip"))), "expected .aab")
    }

    @Test
    fun existingEditIsNeverInsertedCommittedOrDeleted() = runBlocking {
        publisher.publishBinary(request { it.copy(existingEditId = "77") }).assertSuccess {
            assertEquals("77", data.editId)
            assertTrue(data.formattedReport.contains("staged in edit 77 and NOT committed"))
            assertTrue(!data.formattedReport.contains("Published Successfully"))
        }
        assertEquals(listOf("auth", "bundle:app.aab", "track:internal", "validate"), api.calls)
        api.calls.clear()
        api.bundleOverride = ApiResponse.fakeError(payload = "boom")
        publisher.publishBinary(request { it.copy(existingEditId = "77") }).assertError { }
        assertTrue(api.calls.none { it == "delete" })
    }

    @Test
    fun releaseNotesOver500CharsFailLocally() {
        val req = request { it.copy(releaseNotesByLocale = mapOf("en-US" to "x".repeat(501))) }
        assertLocalError(req, "at most 500")
    }
}

private class FakeBinaryApi : PlayPublisherApi {
    val calls = mutableListOf<String>()
    val trackBodies = mutableListOf<JsonObject>()
    val nextCodes = ArrayDeque<Long>()
    var bundleOverride: ApiResponse<PlayBundle>? = null

    private fun code() = nextCodes.removeFirstOrNull() ?: 7L

    override suspend fun authenticate(serviceAccountJson: String): ApiResponse<String> {
        calls += "auth"
        return ApiResponse.fakeSuccess("tok")
    }

    override suspend fun insertEdit(packageName: String, token: String): ApiResponse<PlayAppEdit> {
        calls += "insert"
        return ApiResponse.fakeSuccess(PlayAppEdit("1001"))
    }

    override suspend fun deleteAllImages(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        token: String,
    ): ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    override suspend fun uploadImage(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        imageBytes: ByteArray,
        mimeType: String,
        token: String,
    ): ApiResponse<PlayImage> = ApiResponse.fakeSuccess(PlayImage())

    override suspend fun updateListing(
        packageName: String,
        editId: String,
        language: String,
        listing: PlayListing,
        token: String,
    ): ApiResponse<PlayListing> = ApiResponse.fakeSuccess(listing)

    override suspend fun validateEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<PlayAppEdit> {
        calls += "validate"
        return ApiResponse.fakeSuccess(PlayAppEdit(editId))
    }

    override suspend fun commitEdit(
        packageName: String,
        editId: String,
        changesNotSentForReview: Boolean,
        token: String,
    ): ApiResponse<PlayAppEdit> {
        calls += "commit"
        return ApiResponse.fakeSuccess(PlayAppEdit(editId))
    }

    override suspend fun deleteEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<Unit> {
        calls += "delete"
        return ApiResponse.fakeSuccess(Unit)
    }

    override suspend fun updateTrack(
        packageName: String,
        editId: String,
        track: String,
        trackJson: JsonObject,
        token: String,
    ): ApiResponse<JsonObject> {
        calls += "track:$track"
        trackBodies += trackJson
        return ApiResponse.fakeSuccess(trackJson)
    }

    override suspend fun uploadBundle(
        packageName: String,
        editId: String,
        file: File,
        token: String,
        timeoutMs: Long,
    ): ApiResponse<PlayBundle> {
        calls += "bundle:${file.name}"
        return bundleOverride ?: ApiResponse.fakeSuccess(PlayBundle(code()))
    }

    override suspend fun uploadDeobfuscationFile(
        packageName: String,
        editId: String,
        versionCode: Long,
        type: String,
        file: File,
        token: String,
        timeoutMs: Long,
    ): ApiResponse<Unit> {
        calls += "deob:$versionCode:$type"
        return ApiResponse.fakeSuccess(Unit)
    }
}
