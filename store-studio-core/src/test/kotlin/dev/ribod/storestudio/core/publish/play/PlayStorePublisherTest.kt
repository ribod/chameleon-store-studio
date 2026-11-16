package dev.ribod.storestudio.core.publish.play

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.test.assertError
import com.skydoves.sandwich.test.assertExceptionMessage
import com.skydoves.sandwich.test.assertSuccess
import com.skydoves.sandwich.test.fakeError
import com.skydoves.sandwich.test.fakeException
import com.skydoves.sandwich.test.fakeSuccess
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.core.report.model.StoreListingMetadata
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class PlayStorePublisherTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private val fakeApi = FakePlayPublisherApi()
    private val publisher = PlayStorePublisher(fakeApi)

    private fun sampleAsset(
        slot: StoreDeviceSlot,
        locale: String = "en-US",
        position: Int,
        file: String,
    ) = ExportAsset(
        slotKey = "PLAY_STORE/${slot.name}/$locale/shot$position",
        target = StoreTarget.PLAY_STORE,
        deviceSlot = slot,
        storeLocale = locale,
        id = "shot$position",
        order = position,
        position = position,
        file = file,
        format = ImageFormat.PNG,
        widthPx = 1080,
        heightPx = 1920,
        bytes = 100_000L,
        hasAlpha = false,
        colorSpace = "sRGB",
        pixelSha256 = "pixel$position",
        fileSha256 = "file$position",
        pHash = "0000000000000000",
        md5 = "md5$position",
    )

    @Test
    fun commitsTransactionalEditInAscendingPositionOrder() = runBlocking {
        val exportDir = tempDir.newFolder("export")
        val file1 = File(exportDir, "shot1.png").apply { writeBytes(ByteArray(10)) }
        val file2 = File(exportDir, "shot2.png").apply { writeBytes(ByteArray(20)) }

        val assets = listOf(
            sampleAsset(StoreDeviceSlot.PHONE, "en-US", 2, "shot2.png"),
            sampleAsset(StoreDeviceSlot.PHONE, "en-US", 1, "shot1.png"),
        )
        val listings = mapOf(
            "en-US" to StoreListingMetadata(
                title = "Awesome App",
                shortDescription = "Do more",
                fullDescription = "Best tool",
            ),
        )

        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = assets,
            listingsByLocale = listings,
            dryRun = false,
        )

        val response = publisher.publish(request)
        response.assertSuccess {
            assertEquals(PlayPublishStatus.COMMITTED, data.status)
            assertEquals("1001", data.editId)
            assertTrue(data.formattedReport.contains("Published Successfully"))
        }

        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(listOf("en-US:phoneScreenshots"), fakeApi.deletedImageBuckets)
        val expectedImages = listOf("en-US:phoneScreenshots:10", "en-US:phoneScreenshots:20")
        assertEquals(expectedImages, fakeApi.uploadedImages)
        assertEquals(listOf("en-US:Awesome App"), fakeApi.updatedListings)
        assertEquals(1, fakeApi.validateEditCount)
        assertEquals(1, fakeApi.commitEditCount)
        assertEquals(0, fakeApi.deleteEditCount)
    }

    @Test
    fun dryRunWithCredentialsValidatesAndDeletesEditWithoutCommitting() = runBlocking {
        val exportDir = tempDir.newFolder("export_dry")
        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = emptyList(),
            dryRun = true,
        )

        val response = publisher.publish(request)
        response.assertSuccess {
            assertEquals(PlayPublishStatus.DRY_RUN_VALIDATED, data.status)
            assertEquals("1001", data.editId)
            assertTrue(data.formattedReport.contains("Validated Dry Run"))
        }

        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(1, fakeApi.validateEditCount)
        assertEquals(0, fakeApi.commitEditCount)
        assertEquals(1, fakeApi.deleteEditCount)
    }

    @Test
    fun offlineDryRunWithoutCredentialsGeneratesLocalPlan() = runBlocking {
        val exportDir = tempDir.newFolder("export_offline")
        val assets = listOf(
            sampleAsset(StoreDeviceSlot.FEATURE_GRAPHIC, "en-US", 1, "fg.png"),
        )
        val request = PlayPublishRequest(
            packageName = "com.example.offline",
            serviceAccountJson = null,
            exportDir = exportDir,
            assets = assets,
            dryRun = true,
        )

        val response = publisher.publish(request)
        response.assertSuccess {
            assertEquals(PlayPublishStatus.DRY_RUN_LOCAL, data.status)
            assertTrue(data.formattedReport.contains("Offline Dry Run"))
            assertTrue(data.formattedReport.contains("featureGraphic"))
        }

        assertEquals(0, fakeApi.insertEditCount)
        assertEquals(0, fakeApi.deleteEditCount)
    }

    @Test
    fun rollsBackEditWhenImageUploadFails() = runBlocking {
        val exportDir = tempDir.newFolder("export_fail")
        File(exportDir, "shot1.png").writeBytes(ByteArray(5))
        val assets = listOf(sampleAsset(StoreDeviceSlot.PHONE, "en-US", 1, "shot1.png"))

        fakeApi.uploadImageResponse = ApiResponse.fakeError("Quota exceeded (image upload)")

        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = assets,
            dryRun = false,
        )

        val response = publisher.publish(request)
        response.assertError {
            assertEquals("Quota exceeded (image upload)", payload)
        }

        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(1, fakeApi.deleteEditCount)
        assertEquals(0, fakeApi.commitEditCount)
    }

    @Test
    fun rollsBackEditOnNetworkException() = runBlocking {
        val exportDir = tempDir.newFolder("export_exc")
        val listings = mapOf("en-US" to StoreListingMetadata(title = "App"))

        fakeApi.updateListingResponse = ApiResponse.fakeException(message = "Network timeout")

        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = emptyList(),
            listingsByLocale = listings,
            dryRun = false,
        )

        val response = publisher.publish(request)
        response.assertExceptionMessage("Network timeout")

        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(1, fakeApi.deleteEditCount)
        assertEquals(0, fakeApi.commitEditCount)
    }

    @Test
    fun generatesValidRs256JwtFromSyntheticRsaKeyPair() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        val privEncoder = Base64.getEncoder()
        val privPem = "-----BEGIN PRIVATE KEY-----\n" +
            privEncoder.encodeToString(kp.private.encoded) +
            "\n-----END PRIVATE KEY-----"

        val credentials = ServiceAccountCredentials(
            client_email = "test-service@gcp.iam.gserviceaccount.com",
            private_key = privPem,
            token_uri = "https://oauth2.googleapis.com/token",
        )

        val jwt = PlayServiceAccountJwt.createSignedJwt(credentials)
        val parts = jwt.split(".")
        assertEquals(3, parts.size)

        val verifier = Signature.getInstance("SHA256withRSA")
        verifier.initVerify(kp.public)
        verifier.update("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
        val sigBytes = Base64.getUrlDecoder().decode(parts[2])
        assertTrue(verifier.verify(sigBytes))
    }

    @Test
    fun appliesReleaseNotesToLatestReleaseOfSelectedTrack() = runBlocking {
        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = tempDir.newFolder("export_track"),
            assets = emptyList(),
            track = "beta",
            releaseNotesByLocale = mapOf("en-US" to "New agenda", "es-419" to "Nueva agenda"),
        )

        publisher.publish(request).assertSuccess {
            assertEquals(PlayPublishStatus.COMMITTED, data.status)
            assertTrue(data.formattedReport.contains("[Release Notes for Track 'beta']"))
            assertTrue(data.formattedReport.contains("en-US, es-419"))
        }

        assertEquals(listOf("beta"), fakeApi.requestedTracks)
        val releases = fakeApi.updatedTracks.single()["releases"]!!.jsonArray
        val draft = releases[1].jsonObject
        assertEquals("0.1", draft["userFraction"].toString())
        assertEquals(
            listOf("en-US" to "New agenda", "es-419" to "Nueva agenda"),
            draft["releaseNotes"]!!.jsonArray.map {
                it.jsonObject["language"]!!.jsonPrimitive.content to
                    it.jsonObject["text"]!!.jsonPrimitive.content
            },
        )
        assertEquals(null, releases[0].jsonObject["releaseNotes"])
        assertEquals(1, fakeApi.commitEditCount)
    }

    @Test
    fun trackWithoutReleasesIsReportedAndLeftUnchanged() = runBlocking {
        fakeApi.trackJson = Json.parseToJsonElement("""{"track":"internal"}""").jsonObject
        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = tempDir.newFolder("export_empty_track"),
            assets = emptyList(),
            track = "internal",
            releaseNotesByLocale = mapOf("en-US" to "Notes"),
        )

        publisher.publish(request).assertSuccess {
            assertTrue(data.formattedReport.contains("has no releases yet"))
        }
        assertTrue(fakeApi.updatedTracks.isEmpty())
        assertEquals(1, fakeApi.commitEditCount)
    }

    @Test
    fun withoutTrackNoTrackIsReadOrReported() = runBlocking {
        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = tempDir.newFolder("export_no_track"),
            assets = emptyList(),
            releaseNotesByLocale = mapOf("en-US" to "Notes"),
        )

        publisher.publish(request).assertSuccess {
            assertTrue(!data.formattedReport.contains("Release Notes for Track"))
        }
        assertTrue(fakeApi.requestedTracks.isEmpty())
    }

    private fun partsRequest(parts: Set<PublishPart>, track: String? = "beta"): PlayPublishRequest {
        val exportDir = tempDir.newFolder()
        File(exportDir, "shot1.png").writeBytes(ByteArray(10))
        return PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = listOf(sampleAsset(StoreDeviceSlot.PHONE, "en-US", 1, "shot1.png")),
            listingsByLocale = mapOf("en-US" to StoreListingMetadata(title = "Awesome App")),
            track = track,
            releaseNotesByLocale = mapOf("en-US" to "New agenda"),
            parts = parts,
        )
    }

    @Test
    fun listingOnlyMakesNoImageOrTrackCalls(): Unit = runBlocking {
        publisher.publish(partsRequest(setOf(PublishPart.LISTING))).assertSuccess {
            assertTrue(data.formattedReport.contains("[Text Listings"))
            assertTrue(!data.formattedReport.contains("[Images"))
            assertTrue(!data.formattedReport.contains("Release Notes for Track"))
        }
        assertEquals(listOf("en-US:Awesome App"), fakeApi.updatedListings)
        assertTrue(fakeApi.deletedImageBuckets.isEmpty())
        assertTrue(fakeApi.uploadedImages.isEmpty())
        assertTrue(fakeApi.requestedTracks.isEmpty())
        assertEquals(1, fakeApi.commitEditCount)
    }

    @Test
    fun imagesOnlyDoesNotPatchListingsOrTracks(): Unit = runBlocking {
        publisher.publish(partsRequest(setOf(PublishPart.IMAGES))).assertSuccess {
            assertTrue(data.formattedReport.contains("[Images"))
            assertTrue(!data.formattedReport.contains("[Text Listings"))
        }
        assertEquals(listOf("en-US:phoneScreenshots"), fakeApi.deletedImageBuckets)
        assertEquals(1, fakeApi.uploadedImages.size)
        assertTrue(fakeApi.updatedListings.isEmpty())
        assertTrue(fakeApi.requestedTracks.isEmpty())
        assertEquals(1, fakeApi.commitEditCount)
    }

    @Test
    fun releaseNotesOnlyTouchesTheTrack(): Unit = runBlocking {
        publisher.publish(partsRequest(setOf(PublishPart.RELEASE_NOTES))).assertSuccess {
            assertTrue(data.formattedReport.contains("[Release Notes for Track 'beta']"))
            assertTrue(!data.formattedReport.contains("[Images"))
        }
        assertEquals(listOf("beta"), fakeApi.requestedTracks)
        assertEquals(1, fakeApi.updatedTracks.size)
        assertTrue(fakeApi.updatedListings.isEmpty())
        assertTrue(fakeApi.deletedImageBuckets.isEmpty())
        assertTrue(fakeApi.uploadedImages.isEmpty())
        assertEquals(1, fakeApi.commitEditCount)
    }

    @Test
    fun releaseNotesOnlyWithoutTrackFailsBeforeAnyCall(): Unit = runBlocking {
        val response = publisher.publish(partsRequest(setOf(PublishPart.RELEASE_NOTES), null))
        response.assertError {
            assertTrue(payload.toString().contains("requires a track"))
        }
        assertEquals(0, fakeApi.insertEditCount)
    }

    @Test
    fun offlineDryRunPlanListsOnlySelectedParts(): Unit = runBlocking {
        val request = partsRequest(setOf(PublishPart.LISTING)).copy(
            serviceAccountJson = null,
            dryRun = true,
        )
        publisher.publish(request).assertSuccess {
            assertEquals(PlayPublishStatus.DRY_RUN_LOCAL, data.status)
            assertTrue(data.plan.images.isEmpty())
            assertEquals(1, data.plan.listings.size)
            assertTrue(data.plan.trackReleaseNoteLocales.isEmpty())
        }
    }

    @Test
    fun publishWithBinaryExecutesAtomicEditSuccessfully() = runBlocking {
        val exportDir = tempDir.newFolder("export_atomic")
        val shotFile = File(exportDir, "shot1.png").apply { writeBytes(ByteArray(10)) }
        val aabFile = File(tempDir.root, "app.aab").apply { writeBytes(ByteArray(100)) }
        val mapping = File(tempDir.root, "mapping.txt").apply { writeBytes(ByteArray(50)) }

        val assets = listOf(sampleAsset(StoreDeviceSlot.PHONE, "en-US", 1, "shot1.png"))
        val listings = mapOf(
            "en-US" to StoreListingMetadata(
                title = "Awesome App",
                shortDescription = "Do more",
                fullDescription = "Best tool",
            ),
        )

        val binaryReq = PlayBinaryPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            releaseFiles = listOf(aabFile),
            mappingFile = mapping,
            track = "internal",
        )

        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = assets,
            listingsByLocale = listings,
            binary = binaryReq,
            dryRun = false,
        )

        val response = publisher.publish(request)
        response.assertSuccess {
            assertEquals(PlayPublishStatus.COMMITTED, data.status)
            assertEquals("1001", data.editId)
            assertEquals(listOf(42L), data.versionCodes)
            assertTrue(data.formattedReport.contains("Published Successfully"))
            assertTrue(data.formattedReport.contains("[Binary Release on Track 'internal']"))
        }

        assertEquals(
            listOf(
                "auth",
                "insert",
                "bundle",
                "deob",
                "track",
                "deleteAllImages",
                "uploadImage",
                "updateListing",
                "validate",
                "commit",
            ),
            fakeApi.calls,
        )
        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(1, fakeApi.commitEditCount)
        assertEquals(0, fakeApi.deleteEditCount)
    }

    @Test
    fun publishWithBinaryRollsBackWhenBinaryUploadFails() = runBlocking {
        val exportDir = tempDir.newFolder("export_atomic_fail_bin")
        File(exportDir, "shot1.png").apply { writeBytes(ByteArray(10)) }
        val aabFile = File(tempDir.root, "app_fail.aab").apply { writeBytes(ByteArray(100)) }

        val assets = listOf(sampleAsset(StoreDeviceSlot.PHONE, "en-US", 1, "shot1.png"))
        val binaryReq = PlayBinaryPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            releaseFiles = listOf(aabFile),
            track = "internal",
        )

        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = assets,
            binary = binaryReq,
        )

        fakeApi.uploadBundleResponse = ApiResponse.fakeError("Bundle upload failed")
        val response = publisher.publish(request)
        response.assertError { }

        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(0, fakeApi.commitEditCount)
        assertEquals(1, fakeApi.deleteEditCount)
        assertEquals(listOf("auth", "insert", "bundle", "delete"), fakeApi.calls)
    }

    @Test
    fun publishWithBinaryRollsBackWhenImageUploadFails() = runBlocking {
        val exportDir = tempDir.newFolder("export_atomic_fail_img")
        File(exportDir, "shot1.png").apply { writeBytes(ByteArray(10)) }
        val aabFile = File(tempDir.root, "app_img_fail.aab").apply { writeBytes(ByteArray(100)) }

        val assets = listOf(sampleAsset(StoreDeviceSlot.PHONE, "en-US", 1, "shot1.png"))
        val binaryReq = PlayBinaryPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            releaseFiles = listOf(aabFile),
            track = "internal",
        )

        val request = PlayPublishRequest(
            packageName = "com.example.app",
            serviceAccountJson = "{\"client_email\":\"a@b.com\",\"private_key\":\"\"}",
            exportDir = exportDir,
            assets = assets,
            binary = binaryReq,
        )

        fakeApi.uploadImageResponse = ApiResponse.fakeError("Image upload failed")
        val response = publisher.publish(request)
        response.assertError { }

        assertEquals(1, fakeApi.insertEditCount)
        assertEquals(0, fakeApi.commitEditCount)
        assertEquals(1, fakeApi.deleteEditCount)
        assertEquals(
            listOf("auth", "insert", "bundle", "track", "deleteAllImages", "uploadImage", "delete"),
            fakeApi.calls,
        )
    }
}

private class FakePlayPublisherApi : PlayPublisherApi {
    var authenticateResponse: ApiResponse<String> = ApiResponse.fakeSuccess("fake-oauth-token")
    var insertEditResponse: ApiResponse<PlayAppEdit> = ApiResponse.fakeSuccess(PlayAppEdit("1001"))
    var deleteAllImagesResponse: ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)
    var uploadImageResponse: ApiResponse<PlayImage> = ApiResponse.fakeSuccess(
        PlayImage(id = "img-1", sha256 = "fake-sha"),
    )
    var updateListingResponse: ApiResponse<PlayListing> = ApiResponse.fakeSuccess(
        PlayListing(language = "en-US", title = "Habit"),
    )
    var validateEditResponse: ApiResponse<PlayAppEdit> =
        ApiResponse.fakeSuccess(PlayAppEdit("1001"))
    var commitEditResponse: ApiResponse<PlayAppEdit> =
        ApiResponse.fakeSuccess(PlayAppEdit("1001"))
    var deleteEditResponse: ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)
    var uploadBundleResponse: ApiResponse<PlayBundle> = ApiResponse.fakeSuccess(PlayBundle(42L))
    var uploadDeobfuscationResponse: ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    val calls = mutableListOf<String>()
    val deletedImageBuckets = mutableListOf<String>()
    val uploadedImages = mutableListOf<String>()
    val updatedListings = mutableListOf<String>()
    var insertEditCount = 0
    var validateEditCount = 0
    var commitEditCount = 0
    var deleteEditCount = 0

    override suspend fun authenticate(serviceAccountJson: String): ApiResponse<String> {
        calls += "auth"
        return authenticateResponse
    }

    override suspend fun insertEdit(packageName: String, token: String): ApiResponse<PlayAppEdit> {
        calls += "insert"
        insertEditCount++
        return insertEditResponse
    }

    override suspend fun uploadBundle(
        packageName: String,
        editId: String,
        file: File,
        token: String,
        timeoutMs: Long,
    ): ApiResponse<PlayBundle> {
        calls += "bundle"
        return uploadBundleResponse
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
        calls += "deob"
        return uploadDeobfuscationResponse
    }

    override suspend fun deleteAllImages(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        token: String,
    ): ApiResponse<Unit> {
        calls += "deleteAllImages"
        deletedImageBuckets.add("$language:${imageType.apiValue}")
        return deleteAllImagesResponse
    }

    override suspend fun uploadImage(
        packageName: String,
        editId: String,
        language: String,
        imageType: PlayImageType,
        imageBytes: ByteArray,
        mimeType: String,
        token: String,
    ): ApiResponse<PlayImage> {
        calls += "uploadImage"
        uploadedImages.add("$language:${imageType.apiValue}:${imageBytes.size}")
        return uploadImageResponse
    }

    override suspend fun updateListing(
        packageName: String,
        editId: String,
        language: String,
        listing: PlayListing,
        token: String,
    ): ApiResponse<PlayListing> {
        calls += "updateListing"
        updatedListings.add("$language:${listing.title}")
        return updateListingResponse
    }

    override suspend fun validateEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<PlayAppEdit> {
        calls += "validate"
        validateEditCount++
        return validateEditResponse
    }

    override suspend fun commitEdit(
        packageName: String,
        editId: String,
        changesNotSentForReview: Boolean,
        token: String,
    ): ApiResponse<PlayAppEdit> {
        calls += "commit"
        commitEditCount++
        return commitEditResponse
    }

    override suspend fun deleteEdit(
        packageName: String,
        editId: String,
        token: String,
    ): ApiResponse<Unit> {
        calls += "delete"
        deleteEditCount++
        return deleteEditResponse
    }

    var trackJson: JsonObject = Json.parseToJsonElement(
        """{"track":"beta","releases":[{"status":"completed","versionCodes":["41"]},""" +
            """{"status":"draft","versionCodes":["42"],"userFraction":0.1}]}""",
    ).jsonObject
    val requestedTracks = mutableListOf<String>()
    val updatedTracks = mutableListOf<JsonObject>()

    override suspend fun getTrack(
        packageName: String,
        editId: String,
        track: String,
        token: String,
    ): ApiResponse<JsonObject> {
        requestedTracks.add(track)
        return ApiResponse.fakeSuccess(trackJson)
    }

    override suspend fun updateTrack(
        packageName: String,
        editId: String,
        track: String,
        trackJson: JsonObject,
        token: String,
    ): ApiResponse<JsonObject> {
        calls += "track"
        updatedTracks.add(trackJson)
        return ApiResponse.fakeSuccess(trackJson)
    }
}
