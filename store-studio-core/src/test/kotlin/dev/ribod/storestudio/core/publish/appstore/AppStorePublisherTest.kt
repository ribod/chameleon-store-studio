package dev.ribod.storestudio.core.publish.appstore

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.test.assertError
import com.skydoves.sandwich.test.assertExceptionMessage
import com.skydoves.sandwich.test.assertSuccess
import com.skydoves.sandwich.test.fakeError
import com.skydoves.sandwich.test.fakeException
import com.skydoves.sandwich.test.fakeSuccess
import dev.ribod.storestudio.annotations.StoreTarget
import dev.ribod.storestudio.core.publish.PublishPart
import dev.ribod.storestudio.model.ExportAsset
import dev.ribod.storestudio.model.ImageFormat
import dev.ribod.storestudio.model.StoreDeviceSlot
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppStorePublisherTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private val fakeApi = FakeAppStorePublisherApi()
    private val publisher = AppStorePublisher(fakeApi)

    private fun sampleAsset(
        slot: StoreDeviceSlot,
        locale: String = "en-US",
        position: Int,
        file: String,
    ) = ExportAsset(
        slotKey = "APP_STORE/${slot.name}/$locale/shot$position",
        target = StoreTarget.APP_STORE,
        deviceSlot = slot,
        storeLocale = locale,
        id = "shot$position",
        order = position,
        position = position,
        file = file,
        format = ImageFormat.PNG,
        widthPx = 1320,
        heightPx = 2868,
        bytes = 100_000L,
        hasAlpha = false,
        colorSpace = "sRGB",
        pixelSha256 = "pixel$position",
        fileSha256 = "file$position",
        pHash = "0000000000000000",
        md5 = "md5checksum$position",
    )

    private fun generateTestEcKey(): Pair<String, java.security.PublicKey> {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val keyPair = kpg.generateKeyPair()
        val privB64 = Base64.getEncoder().encodeToString(keyPair.private.encoded)
        val pem = "-----BEGIN PRIVATE KEY-----\n$privB64\n-----END PRIVATE KEY-----"
        return pem to keyPair.public
    }

    @Test
    fun publishExecutesFullLifecycleSuccessfully(): Unit = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder("export")
        val shot1 = exportDir.resolve("shot1.png").apply { writeBytes(ByteArray(2048) { 1 }) }
        val shot2 = exportDir.resolve("shot2.png").apply { writeBytes(ByteArray(2048) { 2 }) }

        val assets = listOf(
            sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot1.png"),
            sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 2, "shot2.png"),
        )

        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("app-123", "apps", AscAppAttributes("com.example.app"))),
            ),
        )
        fakeApi.listVersionsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-456",
                        "appStoreVersions",
                        AscVersionAttributes("1.0.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )
        fakeApi.listLocalizationsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "loc-en",
                        "appStoreVersionLocalizations",
                        AscLocalizationAttributes("en-US"),
                    ),
                ),
            ),
        )
        fakeApi.listScreenshotSetsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "set-iphone67",
                        "appScreenshotSets",
                        AscScreenshotSetAttributes("APP_IPHONE_67"),
                    ),
                ),
            ),
        )
        fakeApi.listScreenshotsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("old-sc-1", "appScreenshots")),
            ),
        )

        var reserveCounter = 1
        fakeApi.reserveScreenshotHandler = { _, fileName, fileSize ->
            val scId = "new-sc-${reserveCounter++}"
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        scId,
                        "appScreenshots",
                        AscScreenshotAttributes(
                            fileName = fileName,
                            fileSize = fileSize,
                            uploadOperations = listOf(
                                AscUploadOperation(
                                    method = "PUT",
                                    url = "https://cdn.apple.com/upload/$scId",
                                    length = fileSize,
                                    offset = 0,
                                ),
                            ),
                            assetDeliveryState = AscAssetDeliveryState("AWAITING_UPLOAD"),
                        ),
                    ),
                ),
            )
        }

        fakeApi.getScreenshotHandler = { scId ->
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        scId,
                        "appScreenshots",
                        AscScreenshotAttributes(
                            assetDeliveryState = AscAssetDeliveryState("COMPLETE"),
                        ),
                    ),
                ),
            )
        }

        val request = AppStorePublishRequest(
            bundleId = "com.example.app",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = assets,
            metadataByLocale = mapOf(
                "en-US" to AppStoreLocalizedMetadata(
                    description = "Updated description",
                    keywords = "test, app",
                    whatsNew = "Bug fixes",
                ),
            ),
            dryRun = false,
            pollIntervalMs = 1L,
        )

        val result = publisher.publish(request)

        result.assertSuccess {
            assertEquals(AppStorePublishStatus.COMMITTED, data.status)
            assertEquals("app-123", data.appId)
            assertEquals("ver-456", data.versionId)
            assertTrue(data.formattedReport.contains("Publication committed successfully"))
        }

        assertEquals(listOf("old-sc-1"), fakeApi.deletedScreenshotIds)
        assertEquals(2, fakeApi.reservedScreenshotNames.size)
        assertEquals(2, fakeApi.executedUploads.size)
        assertEquals(listOf("new-sc-1", "new-sc-2"), fakeApi.committedScreenshotIds)
        assertEquals(
            listOf("new-sc-1", "new-sc-2"),
            fakeApi.reorderedSetScreenshots["set-iphone67"],
        )
        assertNotNull(fakeApi.updatedMetadata["loc-en"])
        assertEquals("Updated description", fakeApi.updatedMetadata["loc-en"]?.description)
    }

    private fun failedUploadDeletions(existing: Int): List<String> = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder()
        exportDir.resolve("shot1.png").writeBytes(ByteArray(2048) { 1 })
        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("app-1", "apps", AscAppAttributes("com.example.app"))),
            ),
        )
        fakeApi.listVersionsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-1",
                        "appStoreVersions",
                        AscVersionAttributes("1.0.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )
        fakeApi.listLocalizationsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "loc-en",
                        "appStoreVersionLocalizations",
                        AscLocalizationAttributes("en-US"),
                    ),
                ),
            ),
        )
        fakeApi.listScreenshotSetsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "set-1",
                        "appScreenshotSets",
                        AscScreenshotSetAttributes("APP_IPHONE_67"),
                    ),
                ),
            ),
        )
        fakeApi.listScreenshotsResponse = ApiResponse.fakeSuccess(
            AscListResponse(List(existing) { AscResource("old-$it", "appScreenshots") }),
        )
        fakeApi.reserveScreenshotHandler = { _, fileName, fileSize ->
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        "new-1",
                        "appScreenshots",
                        AscScreenshotAttributes(
                            fileName = fileName,
                            fileSize = fileSize,
                            uploadOperations = listOf(
                                AscUploadOperation("PUT", "https://cdn.apple.com/u", fileSize, 0),
                            ),
                        ),
                    ),
                ),
            )
        }
        fakeApi.executeUploadResponse = ApiResponse.fakeError("upload failed")

        val result = publisher.publish(
            AppStorePublishRequest(
                bundleId = "com.example.app",
                keyId = "KEY123",
                issuerId = "ISSUER456",
                privateKey = pem,
                exportDir = exportDir,
                assets = listOf(sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot1.png")),
                pollIntervalMs = 1L,
            ),
        )
        assertTrue(result is ApiResponse.Failure)
        fakeApi.deletedScreenshotIds
    }

    @Test
    fun failedUploadKeepsExistingScreenshots() {
        assertEquals(emptyList<String>(), failedUploadDeletions(existing = 2))
    }

    @Test
    fun fullSetDeletesOnlyOverflowBeforeUpload() {
        assertEquals(listOf("old-0"), failedUploadDeletions(existing = 10))
    }

    @Test
    fun publishAuthenticatedDryRunDoesNotMutateRemote(): Unit = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder("export")

        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("app-999", "apps", AscAppAttributes("com.example.dryrun"))),
            ),
        )
        fakeApi.listVersionsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-888",
                        "appStoreVersions",
                        AscVersionAttributes("2.0.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )

        val request = AppStorePublishRequest(
            bundleId = "com.example.dryrun",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = listOf(
                sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot.png"),
            ),
            dryRun = true,
        )

        val result = publisher.publish(request)

        result.assertSuccess {
            assertEquals(AppStorePublishStatus.DRY_RUN_VALIDATED, data.status)
            assertEquals("app-999", data.appId)
            assertEquals("ver-888", data.versionId)
            assertTrue(data.formattedReport.contains("Remote Dry Run"))
        }

        assertTrue(fakeApi.deletedScreenshotIds.isEmpty())
        assertTrue(fakeApi.reservedScreenshotNames.isEmpty())
        assertTrue(fakeApi.executedUploads.isEmpty())
        assertTrue(fakeApi.committedScreenshotIds.isEmpty())
        assertTrue(fakeApi.updatedMetadata.isEmpty())
    }

    @Test
    fun publishOfflineDryRunWorksWithoutCredentials(): Unit = runBlocking {
        val exportDir = tempDir.newFolder("export")
        val request = AppStorePublishRequest(
            bundleId = "com.example.offline",
            keyId = null,
            issuerId = null,
            privateKey = null,
            exportDir = exportDir,
            assets = listOf(
                sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot.png"),
                sampleAsset(StoreDeviceSlot.IPAD_13, "en-US", 1, "ipad.png"),
            ),
            dryRun = true,
        )

        val result = publisher.publish(request)

        result.assertSuccess {
            assertEquals(AppStorePublishStatus.DRY_RUN_LOCAL, data.status)
            assertTrue(data.formattedReport.contains("Offline Dry Run"))
            assertTrue(data.formattedReport.contains("APP_IPHONE_67"))
            assertTrue(data.formattedReport.contains("APP_IPAD_PRO_3GEN_129"))
        }

        assertEquals(0, fakeApi.findAppsCalls.size)
    }

    @Test
    fun publishMissingAppReturnsError(): Unit = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder("export")

        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(AscListResponse(emptyList()))

        val request = AppStorePublishRequest(
            bundleId = "com.example.notfound",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = emptyList(),
            dryRun = false,
        )

        val result = publisher.publish(request)

        result.assertError {
            assertTrue(payload.toString().contains("No application found with bundle ID"))
        }
    }

    @Test
    fun publishNoEditableVersionReturnsError(): Unit = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder("export")

        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("app-1", "apps", AscAppAttributes("com.example.app"))),
            ),
        )
        fakeApi.listVersionsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-1",
                        "appStoreVersions",
                        AscVersionAttributes("1.0.0", "READY_FOR_SALE"),
                    ),
                ),
            ),
        )

        val request = AppStorePublishRequest(
            bundleId = "com.example.app",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = emptyList(),
            dryRun = false,
        )

        val result = publisher.publish(request)

        result.assertError {
            assertTrue(payload.toString().contains("No editable version found"))
        }
    }

    @Test
    fun publishProcessingFailureAbortsWithError(): Unit = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder("export")
        exportDir.resolve("shot.png").apply { writeBytes(ByteArray(100) { 1 }) }

        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("app-1", "apps", AscAppAttributes("com.example.app"))),
            ),
        )
        fakeApi.listVersionsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-1",
                        "appStoreVersions",
                        AscVersionAttributes("1.0.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )
        fakeApi.listLocalizationsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "loc-en",
                        "appStoreVersionLocalizations",
                        AscLocalizationAttributes("en-US"),
                    ),
                ),
            ),
        )
        fakeApi.listScreenshotSetsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "set-1",
                        "appScreenshotSets",
                        AscScreenshotSetAttributes("APP_IPHONE_67"),
                    ),
                ),
            ),
        )
        fakeApi.reserveScreenshotHandler = { _, fileName, fileSize ->
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        "sc-fail",
                        "appScreenshots",
                        AscScreenshotAttributes(
                            fileName = fileName,
                            fileSize = fileSize,
                            uploadOperations = emptyList(),
                        ),
                    ),
                ),
            )
        }
        fakeApi.getScreenshotHandler = { scId ->
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        scId,
                        "appScreenshots",
                        AscScreenshotAttributes(
                            assetDeliveryState = AscAssetDeliveryState("FAILED"),
                        ),
                    ),
                ),
            )
        }

        val request = AppStorePublishRequest(
            bundleId = "com.example.app",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = listOf(sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot.png")),
            dryRun = false,
            pollIntervalMs = 1L,
        )

        val result = publisher.publish(request)

        result.assertError {
            assertTrue(payload.toString().contains("App Store rejected screenshot"))
        }
    }

    @Test
    fun publishNetworkExceptionPropagates(): Unit = runBlocking {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder("export")

        fakeApi.findAppsResponse = ApiResponse.fakeException(message = "Network connection timeout")

        val request = AppStorePublishRequest(
            bundleId = "com.example.app",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = emptyList(),
            dryRun = false,
        )

        val result = publisher.publish(request)

        result.assertExceptionMessage("Network connection timeout")
    }

    private fun partsRequest(
        parts: Set<PublishPart>,
        dryRun: Boolean = false,
    ): AppStorePublishRequest {
        val (pem) = generateTestEcKey()
        val exportDir = tempDir.newFolder()
        exportDir.resolve("shot1.png").writeBytes(ByteArray(16))
        fakeApi.findAppsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(AscResource("app-1", "apps", AscAppAttributes("com.example.app"))),
            ),
        )
        fakeApi.listVersionsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-1",
                        "appStoreVersions",
                        AscVersionAttributes("1.0.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )
        fakeApi.listLocalizationsResponse = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "loc-en",
                        "appStoreVersionLocalizations",
                        AscLocalizationAttributes("en-US"),
                    ),
                ),
            ),
        )
        return AppStorePublishRequest(
            bundleId = "com.example.app",
            keyId = "KEY123",
            issuerId = "ISSUER456",
            privateKey = pem,
            exportDir = exportDir,
            assets = listOf(sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot1.png")),
            metadataByLocale = mapOf(
                "en-US" to AppStoreLocalizedMetadata(
                    description = "Description",
                    keywords = "a, b",
                    promotionalText = "Promo",
                    whatsNew = "Bug fixes",
                    name = "My App",
                    subtitle = "Sub",
                    supportUrl = "https://example.com/support",
                    marketingUrl = "https://example.com",
                ),
                "es-MX" to AppStoreLocalizedMetadata(name = "Mi App"),
            ),
            dryRun = dryRun,
            pollIntervalMs = 1L,
            parts = parts,
        )
    }

    @Test
    fun listingOnlyPatchesListingFieldsAndUploadsNoScreenshots(): Unit = runBlocking {
        publisher.publish(partsRequest(setOf(PublishPart.LISTING))).assertSuccess {
            assertTrue(!data.formattedReport.contains("[Screenshots]"))
        }
        assertEquals(
            AppStoreLocalizedMetadata(
                description = "Description",
                keywords = "a, b",
                promotionalText = "Promo",
                name = "My App",
                subtitle = "Sub",
                supportUrl = "https://example.com/support",
                marketingUrl = "https://example.com",
            ),
            fakeApi.updatedMetadata["loc-en"],
        )
        assertEquals(listOf("info-edit"), fakeApi.listedAppInfoLocalizationsOf)
        assertEquals("My App" to "Sub", fakeApi.updatedAppInfoLocalizations["ail-en"])
        assertEquals(listOf("info-edit:es-MX:Mi App:null"), fakeApi.createdAppInfoLocalizations)
        assertTrue(fakeApi.reservedScreenshotNames.isEmpty())
        assertTrue(fakeApi.deletedScreenshotIds.isEmpty())
    }

    @Test
    fun releaseNotesOnlyPatchesWhatsNew(): Unit = runBlocking {
        publisher.publish(partsRequest(setOf(PublishPart.RELEASE_NOTES))).assertSuccess {
            assertTrue(data.formattedReport.contains("WhatsNew="))
            assertTrue(!data.formattedReport.contains("Keywords="))
        }
        assertEquals(
            AppStoreLocalizedMetadata(whatsNew = "Bug fixes"),
            fakeApi.updatedMetadata["loc-en"],
        )
        assertTrue(fakeApi.reservedScreenshotNames.isEmpty())
        assertEquals(0, fakeApi.appInfoCalls)
    }

    @Test
    fun imagesOnlyUploadsScreenshotsWithoutPatchingMetadata(): Unit = runBlocking {
        publisher.publish(partsRequest(setOf(PublishPart.IMAGES))).assertSuccess {
            assertTrue(data.formattedReport.contains("[Screenshots]"))
            assertTrue(!data.formattedReport.contains("[Metadata]"))
        }
        assertEquals(listOf("shot1.png"), fakeApi.reservedScreenshotNames)
        assertTrue(fakeApi.updatedMetadata.isEmpty())
        assertEquals(0, fakeApi.appInfoCalls)
    }

    @Test
    fun offlineDryRunPlanListsOnlySelectedParts(): Unit = runBlocking {
        val request = partsRequest(setOf(PublishPart.RELEASE_NOTES), dryRun = true)
            .copy(privateKey = null)
        publisher.publish(request).assertSuccess {
            assertEquals(AppStorePublishStatus.DRY_RUN_LOCAL, data.status)
            assertTrue(data.plan.buckets.isEmpty())
            assertEquals(
                AppStoreLocalizedMetadata(whatsNew = "Bug fixes"),
                data.plan.metadataByLocale["en-US"],
            )
        }
    }

    @Test
    fun jwtTokenGenerationAndJoseSignatureValidation() {
        val (pem, publicKey) = generateTestEcKey()
        val token = AppStoreConnectJwt.generateToken(
            keyId = "TEST_KEY_ID",
            issuerId = "TEST_ISSUER_ID",
            privateKeyPemOrPath = pem,
        )

        val parts = token.split(".")
        assertEquals(3, parts.size)

        val headerJson = String(Base64.getUrlDecoder().decode(parts[0]))
        assertTrue(headerJson.contains("\"alg\":\"ES256\""))
        assertTrue(headerJson.contains("\"kid\":\"TEST_KEY_ID\""))

        val claimsJson = String(Base64.getUrlDecoder().decode(parts[1]))
        assertTrue(claimsJson.contains("\"iss\":\"TEST_ISSUER_ID\""))
        assertTrue(claimsJson.contains("\"aud\":\"appstoreconnect-v1\""))

        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8)
        val signatureBytes = Base64.getUrlDecoder().decode(parts[2])
        assertEquals(64, signatureBytes.size)

        val verifier = Signature.getInstance("SHA256withECDSAinP1363Format").apply {
            initVerify(publicKey)
            update(signingInput)
        }
        assertTrue(
            "JOSE 64-byte IEEE P1363 signature verification failed",
            verifier.verify(signatureBytes),
        )
    }

    @Test
    fun publishUnifiedIpaListingScreenshotsAndSubmitForReviewOrdersCallsCorrectly(): Unit =
        runBlocking {
            val (pem) = generateTestEcKey()
            val exportDir = tempDir.newFolder("export_unified")
            exportDir.resolve("shot1.png").writeBytes(ByteArray(128) { 1 })

            val ipaFile = tempDir.newFile("unified.ipa")
            ZipOutputStream(ipaFile.outputStream()).use { z ->
                z.putNextEntry(ZipEntry("Payload/Demo.app/Info.plist"))
                val dict = com.dd.plist.NSDictionary().apply {
                    put("CFBundleShortVersionString", "2.0.0")
                    put("CFBundleVersion", "99")
                }
                z.write(dict.toXMLPropertyList().toByteArray())
                z.closeEntry()
                z.putNextEntry(ZipEntry("Payload/Demo.app/bin"))
                z.write(ByteArray(64) { 3 })
                z.closeEntry()
            }

            val unifiedApi = FakeUnifiedAppStoreApi()
            val unifiedPublisher = AppStorePublisher(unifiedApi)
            val outcome = unifiedPublisher.publish(
                AppStorePublishRequest(
                    bundleId = "com.example.app",
                    keyId = "KEY",
                    issuerId = "ISS",
                    privateKey = pem,
                    exportDir = exportDir,
                    assets = listOf(
                        sampleAsset(StoreDeviceSlot.IPHONE_6_9, "en-US", 1, "shot1.png"),
                    ),
                    metadataByLocale = mapOf(
                        "en-US" to AppStoreLocalizedMetadata(
                            name = "My App",
                            description = "Awesome",
                            whatsNew = "Bug fixes",
                        ),
                    ),
                    pollIntervalMs = 1L,
                    binary = AppStoreBinaryPublishRequest(
                        bundleId = "com.example.app",
                        keyId = "KEY",
                        issuerId = "ISS",
                        privateKey = pem,
                        ipaFile = ipaFile,
                        whatToTestByLocale = mapOf("en-US" to "Test new UI"),
                        betaGroups = listOf("Internal"),
                        submitBetaReview = true,
                        submitForReview = true,
                        pollIntervalMs = 1L,
                    ),
                ),
            )
            outcome.assertSuccess()
            val data = (outcome as ApiResponse.Success).data
            assertEquals(AppStorePublishStatus.COMMITTED, data.status)
            assertEquals("build-99", data.buildId)
            assertEquals("2.0.0", data.versionString)
            assertEquals("99", data.buildNumber)
            assertTrue(data.formattedReport.contains("[IPA Build Upload]"))
            assertTrue(data.formattedReport.contains("Build ID: build-99"))

            // Single lookup of app and editable versions
            assertEquals(1, unifiedApi.callOrder.count { it == "findApps" })
            assertEquals(1, unifiedApi.callOrder.count { it == "listEditableVersions" })

            val uploadIdx = unifiedApi.callOrder.indexOf("createBuildUpload")
            val reorderIdx = unifiedApi.callOrder.indexOf("reorderScreenshots")
            val updateLocIdx = unifiedApi.callOrder.indexOf("updateLocalizationMetadata")
            val updateInfoIdx = unifiedApi.callOrder.indexOf("updateAppInfoLocalization")
            val attachIdx = unifiedApi.callOrder.indexOf("attachBuildToVersion")
            val submitIdx = unifiedApi.callOrder.indexOf("submitReviewSubmission")

            assertTrue("IPA upload should precede attach", uploadIdx in 0 until attachIdx)
            assertTrue("Screenshots must finish before attach", reorderIdx in 0 until attachIdx)
            assertTrue(
                "Version localization must finish before attach",
                updateLocIdx in 0 until attachIdx,
            )
            assertTrue(
                "AppInfo localization must finish before attach",
                updateInfoIdx in 0 until attachIdx,
            )
            assertTrue("Attach must precede review submission", attachIdx < submitIdx)
        }
}

class FakeAppStorePublisherApi : AppStorePublisherApi {
    val findAppsCalls = mutableListOf<String>()
    var findAppsResponse: ApiResponse<AscListResponse<AscAppAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    var listVersionsResponse: ApiResponse<AscListResponse<AscVersionAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    var listLocalizationsResponse: ApiResponse<AscListResponse<AscLocalizationAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    var listScreenshotSetsResponse: ApiResponse<AscListResponse<AscScreenshotSetAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    var createScreenshotSetResponse: ApiResponse<AscSingleResponse<AscScreenshotSetAttributes>> =
        ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource("new-set", "appScreenshotSets")),
        )

    var listScreenshotsResponse: ApiResponse<AscListResponse<AscScreenshotAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    val deletedScreenshotIds = mutableListOf<String>()
    var deleteScreenshotResponse: ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    val reservedScreenshotNames = mutableListOf<String>()
    var reserveScreenshotHandler: (
        String,
        String,
        Long,
    ) -> ApiResponse<AscSingleResponse<AscScreenshotAttributes>> =
        { _, name, size ->
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        "sc-id",
                        "appScreenshots",
                        AscScreenshotAttributes(fileSize = size, fileName = name),
                    ),
                ),
            )
        }

    val executedUploads = mutableListOf<AscUploadOperation>()
    var executeUploadResponse: ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    val committedScreenshotIds = mutableListOf<String>()
    var commitScreenshotResponse: ApiResponse<AscSingleResponse<AscScreenshotAttributes>> =
        ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource("sc-id", "appScreenshots")),
        )

    var getScreenshotHandler: (String) -> ApiResponse<AscSingleResponse<AscScreenshotAttributes>> =
        { scId ->
            ApiResponse.fakeSuccess(
                AscSingleResponse(
                    AscResource(
                        scId,
                        "appScreenshots",
                        AscScreenshotAttributes(
                            assetDeliveryState = AscAssetDeliveryState("COMPLETE"),
                        ),
                    ),
                ),
            )
        }

    val reorderedSetScreenshots = mutableMapOf<String, List<String>>()
    var reorderScreenshotsResponse: ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    val updatedMetadata = mutableMapOf<String, AppStoreLocalizedMetadata>()
    var updateLocalizationResponse: ApiResponse<AscSingleResponse<AscLocalizationAttributes>> =
        ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource("loc-id", "appStoreVersionLocalizations")),
        )

    override suspend fun findApps(
        token: String,
        bundleId: String,
    ): ApiResponse<AscListResponse<AscAppAttributes>> {
        findAppsCalls.add(bundleId)
        return findAppsResponse
    }

    override suspend fun listEditableVersions(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscVersionAttributes>> = listVersionsResponse

    override suspend fun listVersionLocalizations(
        token: String,
        versionId: String,
    ): ApiResponse<AscListResponse<AscLocalizationAttributes>> = listLocalizationsResponse

    override suspend fun listScreenshotSets(
        token: String,
        localizationId: String,
    ): ApiResponse<AscListResponse<AscScreenshotSetAttributes>> = listScreenshotSetsResponse

    override suspend fun createScreenshotSet(
        token: String,
        localizationId: String,
        displayType: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotSetAttributes>> = createScreenshotSetResponse

    override suspend fun listScreenshots(
        token: String,
        screenshotSetId: String,
    ): ApiResponse<AscListResponse<AscScreenshotAttributes>> = listScreenshotsResponse

    override suspend fun deleteScreenshot(token: String, screenshotId: String): ApiResponse<Unit> {
        deletedScreenshotIds.add(screenshotId)
        return deleteScreenshotResponse
    }

    override suspend fun reserveScreenshot(
        token: String,
        screenshotSetId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> {
        reservedScreenshotNames.add(fileName)
        return reserveScreenshotHandler(screenshotSetId, fileName, fileSize)
    }

    override suspend fun executeUploadOperation(
        operation: AscUploadOperation,
        chunkBytes: ByteArray,
    ): ApiResponse<Unit> {
        executedUploads.add(operation)
        return executeUploadResponse
    }

    override suspend fun commitScreenshot(
        token: String,
        screenshotId: String,
        md5Checksum: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> {
        committedScreenshotIds.add(screenshotId)
        return commitScreenshotResponse
    }

    override suspend fun getScreenshot(
        token: String,
        screenshotId: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> = getScreenshotHandler(screenshotId)

    override suspend fun reorderScreenshots(
        token: String,
        screenshotSetId: String,
        screenshotIds: List<String>,
    ): ApiResponse<Unit> {
        reorderedSetScreenshots[screenshotSetId] = screenshotIds
        return reorderScreenshotsResponse
    }

    override suspend fun updateLocalizationMetadata(
        token: String,
        localizationId: String,
        metadata: AppStoreLocalizedMetadata,
    ): ApiResponse<AscSingleResponse<AscLocalizationAttributes>> {
        updatedMetadata[localizationId] = metadata
        return updateLocalizationResponse
    }

    var appInfoCalls = 0
    var listAppInfosResponse: ApiResponse<AscListResponse<AscAppInfoAttributes>> =
        ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "info-live",
                        "appInfos",
                        AscAppInfoAttributes("READY_FOR_DISTRIBUTION"),
                    ),
                    AscResource(
                        "info-edit",
                        "appInfos",
                        AscAppInfoAttributes("PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )
    var listAppInfoLocalizationsResponse:
        ApiResponse<AscListResponse<AscAppInfoLocalizationAttributes>> = ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ail-en",
                        "appInfoLocalizations",
                        AscAppInfoLocalizationAttributes("en-US"),
                    ),
                ),
            ),
        )
    val listedAppInfoLocalizationsOf = mutableListOf<String>()
    val updatedAppInfoLocalizations = mutableMapOf<String, Pair<String?, String?>>()
    val createdAppInfoLocalizations = mutableListOf<String>()

    override suspend fun listAppInfos(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscAppInfoAttributes>> {
        appInfoCalls++
        return listAppInfosResponse
    }

    override suspend fun listAppInfoLocalizations(
        token: String,
        appInfoId: String,
    ): ApiResponse<AscListResponse<AscAppInfoLocalizationAttributes>> {
        appInfoCalls++
        listedAppInfoLocalizationsOf.add(appInfoId)
        return listAppInfoLocalizationsResponse
    }

    override suspend fun updateAppInfoLocalization(
        token: String,
        appInfoLocalizationId: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>> {
        appInfoCalls++
        updatedAppInfoLocalizations[appInfoLocalizationId] = name to subtitle
        return ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource(appInfoLocalizationId, "appInfoLocalizations")),
        )
    }

    override suspend fun createAppInfoLocalization(
        token: String,
        appInfoId: String,
        locale: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>> {
        appInfoCalls++
        createdAppInfoLocalizations.add("$appInfoId:$locale:$name:$subtitle")
        return ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource("ail-new", "appInfoLocalizations")),
        )
    }
}

private class FakeUnifiedAppStoreApi : AppStorePublisherApi {
    val callOrder = mutableListOf<String>()

    override suspend fun findApps(
        token: String,
        bundleId: String,
    ): ApiResponse<AscListResponse<AscAppAttributes>> {
        callOrder += "findApps"
        return ApiResponse.fakeSuccess(
            AscListResponse(listOf(AscResource("app-1", "apps", AscAppAttributes(bundleId)))),
        )
    }

    override suspend fun listEditableVersions(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscVersionAttributes>> {
        callOrder += "listEditableVersions"
        return ApiResponse.fakeSuccess(
            AscListResponse(
                listOf(
                    AscResource(
                        "ver-200",
                        "appStoreVersions",
                        AscVersionAttributes("2.0.0", "PREPARE_FOR_SUBMISSION"),
                    ),
                ),
            ),
        )
    }

    override suspend fun listVersionLocalizations(
        token: String,
        versionId: String,
    ): ApiResponse<AscListResponse<AscLocalizationAttributes>> = ApiResponse.fakeSuccess(
        AscListResponse(
            listOf(
                AscResource(
                    "loc-en",
                    "appStoreVersionLocalizations",
                    AscLocalizationAttributes(locale = "en-US"),
                ),
            ),
        ),
    )

    override suspend fun listScreenshotSets(
        token: String,
        localizationId: String,
    ): ApiResponse<AscListResponse<AscScreenshotSetAttributes>> = ApiResponse.fakeSuccess(
        AscListResponse(
            listOf(
                AscResource(
                    "set-1",
                    "appScreenshotSets",
                    AscScreenshotSetAttributes("APP_IPHONE_67"),
                ),
            ),
        ),
    )

    override suspend fun createScreenshotSet(
        token: String,
        localizationId: String,
        displayType: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotSetAttributes>> =
        ApiResponse.fakeSuccess(AscSingleResponse(AscResource("set-1", "appScreenshotSets")))

    override suspend fun listScreenshots(
        token: String,
        screenshotSetId: String,
    ): ApiResponse<AscListResponse<AscScreenshotAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    override suspend fun deleteScreenshot(token: String, screenshotId: String): ApiResponse<Unit> =
        ApiResponse.fakeSuccess(Unit)

    override suspend fun reserveScreenshot(
        token: String,
        screenshotSetId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> = ApiResponse.fakeSuccess(
        AscSingleResponse(
            AscResource(
                "sc-1",
                "appScreenshots",
                AscScreenshotAttributes(fileSize = fileSize, fileName = fileName),
            ),
        ),
    )

    override suspend fun executeUploadOperation(
        operation: AscUploadOperation,
        chunkBytes: ByteArray,
    ): ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    override suspend fun commitScreenshot(
        token: String,
        screenshotId: String,
        md5Checksum: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> =
        ApiResponse.fakeSuccess(AscSingleResponse(AscResource(screenshotId, "appScreenshots")))

    override suspend fun getScreenshot(
        token: String,
        screenshotId: String,
    ): ApiResponse<AscSingleResponse<AscScreenshotAttributes>> = ApiResponse.fakeSuccess(
        AscSingleResponse(
            AscResource(
                screenshotId,
                "appScreenshots",
                AscScreenshotAttributes(assetDeliveryState = AscAssetDeliveryState("COMPLETE")),
            ),
        ),
    )

    override suspend fun reorderScreenshots(
        token: String,
        screenshotSetId: String,
        screenshotIds: List<String>,
    ): ApiResponse<Unit> {
        callOrder += "reorderScreenshots"
        return ApiResponse.fakeSuccess(Unit)
    }

    override suspend fun updateLocalizationMetadata(
        token: String,
        localizationId: String,
        metadata: AppStoreLocalizedMetadata,
    ): ApiResponse<AscSingleResponse<AscLocalizationAttributes>> {
        callOrder += "updateLocalizationMetadata"
        return ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource(localizationId, "appStoreVersionLocalizations")),
        )
    }

    override suspend fun listAppInfos(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscAppInfoAttributes>> = ApiResponse.fakeSuccess(
        AscListResponse(
            listOf(
                AscResource(
                    "info-1",
                    "appInfos",
                    AscAppInfoAttributes("PREPARE_FOR_SUBMISSION"),
                ),
            ),
        ),
    )

    override suspend fun listAppInfoLocalizations(
        token: String,
        appInfoId: String,
    ): ApiResponse<AscListResponse<AscAppInfoLocalizationAttributes>> = ApiResponse.fakeSuccess(
        AscListResponse(
            listOf(
                AscResource(
                    "ail-en",
                    "appInfoLocalizations",
                    AscAppInfoLocalizationAttributes("en-US"),
                ),
            ),
        ),
    )

    override suspend fun updateAppInfoLocalization(
        token: String,
        appInfoLocalizationId: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>> {
        callOrder += "updateAppInfoLocalization"
        return ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource(appInfoLocalizationId, "appInfoLocalizations")),
        )
    }

    override suspend fun createAppInfoLocalization(
        token: String,
        appInfoId: String,
        locale: String,
        name: String?,
        subtitle: String?,
    ): ApiResponse<AscSingleResponse<AscAppInfoLocalizationAttributes>> =
        ApiResponse.fakeSuccess(AscSingleResponse(AscResource("ail-new", "appInfoLocalizations")))

    override suspend fun listBetaGroups(
        token: String,
        appId: String,
    ): ApiResponse<AscListResponse<AscBetaGroupAttributes>> = ApiResponse.fakeSuccess(
        AscListResponse(
            listOf(AscResource("grp-1", "betaGroups", AscBetaGroupAttributes("Internal"))),
        ),
    )

    override suspend fun createBuildUpload(
        token: String,
        appId: String,
        shortVersion: String,
        buildVersion: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscBuildUploadAttributes>> {
        callOrder += "createBuildUpload"
        return ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource("bu-1", "buildUploads", AscBuildUploadAttributes())),
        )
    }

    override suspend fun createBuildUploadFile(
        token: String,
        buildUploadId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> = ApiResponse.fakeSuccess(
        AscSingleResponse(AscResource("buf-1", "buildUploadFiles", AscBuildUploadFileAttributes())),
    )

    override suspend fun updateBuildUploadFile(
        token: String,
        fileId: String,
        uploaded: Boolean,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> = ApiResponse.fakeSuccess(
        AscSingleResponse(
            AscResource(
                fileId,
                "buildUploadFiles",
                AscBuildUploadFileAttributes(uploaded = uploaded),
            ),
        ),
    )

    override suspend fun getBuildUpload(
        token: String,
        buildUploadId: String,
    ): ApiResponse<AscBuildUploadResponse> = ApiResponse.fakeSuccess(
        AscBuildUploadResponse(
            AscBuildUploadData(
                id = buildUploadId,
                attributes = AscBuildUploadAttributes(state = AscBuildUploadState("COMPLETE")),
                relationships = AscBuildUploadRelationships(
                    build = AscNullableRelationship(AscRelationshipItem("builds", "build-99")),
                ),
            ),
        ),
    )

    override suspend fun listBetaBuildLocalizations(
        token: String,
        buildId: String,
    ): ApiResponse<AscListResponse<AscBetaBuildLocalizationAttributes>> =
        ApiResponse.fakeSuccess(AscListResponse())

    override suspend fun createBetaBuildLocalization(
        token: String,
        buildId: String,
        locale: String,
        whatsNew: String,
    ): ApiResponse<AscSingleResponse<AscBetaBuildLocalizationAttributes>> = ApiResponse.fakeSuccess(
        AscSingleResponse(AscResource("bbl-1", "betaBuildLocalizations")),
    )

    override suspend fun addBuildToBetaGroups(
        token: String,
        buildId: String,
        groupIds: List<String>,
    ): ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    override suspend fun submitBetaReview(token: String, buildId: String): ApiResponse<Unit> =
        ApiResponse.fakeSuccess(Unit)

    override suspend fun attachBuildToVersion(
        token: String,
        versionId: String,
        buildId: String,
    ): ApiResponse<Unit> {
        callOrder += "attachBuildToVersion"
        return ApiResponse.fakeSuccess(Unit)
    }

    override suspend fun createReviewSubmission(
        token: String,
        appId: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> = ApiResponse.fakeSuccess(
        AscSingleResponse(
            AscResource("sub-1", "reviewSubmissions", AscReviewSubmissionAttributes()),
        ),
    )

    override suspend fun createReviewSubmissionItem(
        token: String,
        reviewSubmissionId: String,
        versionId: String,
    ): ApiResponse<Unit> = ApiResponse.fakeSuccess(Unit)

    override suspend fun submitReviewSubmission(
        token: String,
        reviewSubmissionId: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> {
        callOrder += "submitReviewSubmission"
        return ApiResponse.fakeSuccess(
            AscSingleResponse(AscResource(reviewSubmissionId, "reviewSubmissions")),
        )
    }
}
