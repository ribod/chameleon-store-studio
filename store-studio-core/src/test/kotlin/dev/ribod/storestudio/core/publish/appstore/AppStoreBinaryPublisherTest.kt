@file:Suppress("ktlint:standard:max-line-length")

package dev.ribod.storestudio.core.publish.appstore

import com.dd.plist.NSDictionary
import com.dd.plist.PropertyListParser
import com.skydoves.sandwich.ApiResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AppStoreBinaryPublisherTest {

    @get:Rule
    val tempDir: TemporaryFolder = TemporaryFolder()

    private val api = FakeBinaryApi()
    private val publisher = AppStorePublisher(api)

    private val pem: String by lazy {
        val kp = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        "-----BEGIN PRIVATE KEY-----\n${Base64.getEncoder().encodeToString(
            kp.private.encoded,
        )}\n-----END PRIVATE KEY-----"
    }

    private fun ipa(binary: Boolean = false, short: String = "1.2.3", build: String = "45"): File {
        val dict = NSDictionary().apply {
            put("CFBundleShortVersionString", short)
            put("CFBundleVersion", build)
        }
        val file = tempDir.newFile("app${System.nanoTime()}.ipa")
        ZipOutputStream(file.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("Payload/Demo.app/Info.plist"))
            if (binary) {
                com.dd.plist.BinaryPropertyListWriter.write(z, dict)
            } else {
                z.write(dict.toXMLPropertyList().toByteArray())
            }
            z.closeEntry()
            z.putNextEntry(ZipEntry("Payload/Demo.app/blob"))
            z.write(ByteArray(2500) { (it * 7).toByte() })
            z.closeEntry()
        }
        return file
    }

    private fun request(
        file: File,
        withCreds: Boolean = true,
        block: (AppStoreBinaryPublishRequest) -> AppStoreBinaryPublishRequest = { it },
    ) = block(
        AppStoreBinaryPublishRequest(
            bundleId = "dev.demo",
            keyId = "K".takeIf { withCreds },
            issuerId = "I".takeIf { withCreds },
            privateKey = pem.takeIf { withCreds },
            ipaFile = file,
            pollIntervalMs = 1,
            processingTimeoutMs = 5_000,
        ),
    )

    private fun ok(r: ApiResponse<AppStoreBinaryPublishOutcome>) =
        (r as? ApiResponse.Success)?.data ?: error("expected success but was $r")

    private fun err(r: ApiResponse<AppStoreBinaryPublishOutcome>): String =
        (r as ApiResponse.Failure.Error).payload.toString()

    @Test
    fun happyPathUploadsChunksAndCompletes(): Unit = runBlocking {
        val f = ipa()
        val out = ok(
            publisher.publishBinary(
                request(f) {
                    it.copy(
                        whatToTestByLocale = mapOf("en-US" to "New"),
                        betaGroups = listOf("Testers"),
                        submitBetaReview = true,
                    )
                },
            ),
        )
        assertEquals(AppStorePublishStatus.COMMITTED, out.status)
        assertEquals("build-1", out.buildId)
        assertEquals("1.2.3", out.version)
        assertEquals("45", out.buildNumber)
        val bytes = f.readBytes()
        assertEquals(3, api.chunks.size)
        var pos = 0L
        for ((op, data) in api.chunks) {
            assertEquals(pos, op.offset)
            assertArrayEquals(
                bytes.copyOfRange(op.offset.toInt(), (op.offset + op.length).toInt()),
                data,
            )
            pos += op.length
        }
        assertEquals(bytes.size.toLong(), pos)
        assertEquals(
            listOf("createUpload", "markUploaded", "created en-US", "groups g1", "betaReview"),
            api.calls.filter {
                it !=
                    "upload"
            },
        )
    }

    @Test
    fun readsVersionsFromBinaryPlist() {
        val (s, b) = readIpaVersions(ipa(binary = true, short = "9.0", build = "7"))
        assertEquals("9.0", s)
        assertEquals("7", b)
        // sanity: it really is a binary plist
        assertTrue(PropertyListParser.parse(binaryBytes()) is NSDictionary)
    }

    private fun binaryBytes(): ByteArray {
        val o = java.io.ByteArrayOutputStream()
        com.dd.plist.BinaryPropertyListWriter.write(o, NSDictionary())
        return o.toByteArray().also { assertEquals("bplist", String(it, 0, 6)) }
    }

    @Test
    fun explicitVersionsOverridePlist(): Unit = runBlocking {
        val out = ok(
            publisher.publishBinary(
                request(ipa(), withCreds = false) {
                    it.copy(dryRun = true, bundleShortVersion = "5.5.5", bundleVersion = "99")
                },
            ),
        )
        assertEquals("5.5.5", out.version)
        assertEquals("99", out.buildNumber)
    }

    @Test
    fun dryRunWithoutCredentialsIsLocalAndTouchesNoNetwork(): Unit = runBlocking {
        val out =
            ok(
                publisher.publishBinary(
                    request(ipa(), withCreds = false) {
                        it.copy(dryRun = true)
                    },
                ),
            )
        assertEquals(AppStorePublishStatus.DRY_RUN_LOCAL, out.status)
        assertTrue(api.calls.isEmpty())
        assertEquals("1.2.3", out.version)
    }

    @Test
    fun missingCredentialsNamesEnvVars(): Unit = runBlocking {
        val msg = err(publisher.publishBinary(request(ipa(), withCreds = false)))
        assertTrue(msg, "ASC_KEY_ID" in msg && "ASC_ISSUER_ID" in msg && "ASC_PRIVATE_KEY" in msg)
    }

    @Test
    fun localValidationRejectsMissingAndNonIpa(): Unit = runBlocking {
        val missing = File(tempDir.root, "nope.ipa")
        assertTrue("not found" in err(publisher.publishBinary(request(missing))))
        val txt = tempDir.newFile("a.zip")
        assertTrue(".ipa" in err(publisher.publishBinary(request(txt))))
        val noPlist = tempDir.newFile("b.ipa").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue("bundleShortVersion" in err(publisher.publishBinary(request(noPlist))))
    }

    @Test
    fun processingFailedSurfacesApiErrors(): Unit = runBlocking {
        api.finalState = AscBuildUploadState(
            "FAILED",
            errors = listOf(AscStateDetail("90382", "Invalid bundle")),
        )
        val msg = err(publisher.publishBinary(request(ipa())))
        assertTrue(msg, "90382" in msg && "Invalid bundle" in msg)
        assertTrue(api.calls.none { it.startsWith("created") })
    }

    @Test
    fun pollTimeoutFails(): Unit = runBlocking {
        api.finalState = AscBuildUploadState("PROCESSING")
        val msg = err(publisher.publishBinary(request(ipa()) { it.copy(processingTimeoutMs = 30) }))
        assertTrue(msg, "Timed out" in msg && "PROCESSING" in msg)
    }

    @Test
    fun unknownBetaGroupFailsBeforeUpload(): Unit = runBlocking {
        val msg =
            err(publisher.publishBinary(request(ipa()) { it.copy(betaGroups = listOf("Nope")) }))
        assertTrue(msg, "Nope" in msg && "Testers" in msg)
        assertTrue(api.chunks.isEmpty())
        assertNull(api.calls.firstOrNull { it == "createUpload" })
    }

    @Test
    fun dryRunWithCredentialsValidatesWithoutUploading(): Unit = runBlocking {
        val out = ok(
            publisher.publishBinary(
                request(ipa()) {
                    it.copy(dryRun = true, betaGroups = listOf("Testers"))
                },
            ),
        )
        assertEquals(AppStorePublishStatus.DRY_RUN_VALIDATED, out.status)
        assertTrue(api.chunks.isEmpty() && "createUpload" !in api.calls)
    }

    @Test
    fun submitForReviewIsOffByDefaultAndWorksWhenOn(): Unit = runBlocking {
        ok(publisher.publishBinary(request(ipa())))
        assertTrue(
            api.calls.none {
                it in
                    setOf("attach", "reviewSubmission", "reviewItem", "reviewSubmit")
            },
        )
        api.calls.clear()
        ok(publisher.publishBinary(request(ipa()) { it.copy(submitForReview = true) }))
        assertEquals(
            listOf("attach", "reviewSubmission", "reviewItem", "reviewSubmit"),
            api.calls.filter {
                !it.startsWith("upload") &&
                    it != "markUploaded" &&
                    it != "createUpload"
            },
        )
    }
}

private class FakeBinaryApi : AppStorePublisherApi {
    val calls = mutableListOf<String>()
    val chunks = mutableListOf<Pair<AscUploadOperation, ByteArray>>()
    var finalState = AscBuildUploadState("COMPLETE")
    private var polls = 0

    private fun <T> ok(v: T) = ApiResponse.Success(v)

    override suspend fun findApps(token: String, bundleId: String) = ok(
        AscListResponse(
            listOf(AscResource("app1", "apps", AscAppAttributes(bundleId = bundleId))),
        ),
    )

    override suspend fun listEditableVersions(token: String, appId: String) = ok(
        AscListResponse(
            listOf(AscResource("v1", "appStoreVersions", AscVersionAttributes("1.2.3"))),
        ),
    )

    override suspend fun createBuildUpload(
        token: String,
        appId: String,
        shortVersion: String,
        buildVersion: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscBuildUploadAttributes>> {
        calls += "createUpload"
        return ok(AscSingleResponse(AscResource("bu1", "buildUploads", AscBuildUploadAttributes())))
    }

    override suspend fun createBuildUploadFile(
        token: String,
        buildUploadId: String,
        fileName: String,
        fileSize: Long,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> {
        val part = (fileSize + 2) / 3
        val ops = (0 until 3).map { i ->
            val off = i * part
            AscUploadOperation("PUT", "https://upload/$i", minOf(part, fileSize - off), off)
        }
        return ok(
            AscSingleResponse(
                AscResource(
                    "f1",
                    "buildUploadFiles",
                    AscBuildUploadFileAttributes(uploadOperations = ops),
                ),
            ),
        )
    }

    override suspend fun executeUploadOperation(
        operation: AscUploadOperation,
        chunkBytes: ByteArray,
    ): ApiResponse<Unit> {
        chunks += operation to chunkBytes
        calls += "upload"
        return ok(Unit)
    }

    override suspend fun updateBuildUploadFile(
        token: String,
        fileId: String,
        uploaded: Boolean,
    ): ApiResponse<AscSingleResponse<AscBuildUploadFileAttributes>> {
        calls += "markUploaded"
        return ok(
            AscSingleResponse(
                AscResource("f1", "buildUploadFiles", AscBuildUploadFileAttributes()),
            ),
        )
    }

    override suspend fun getBuildUpload(
        token: String,
        buildUploadId: String,
    ): ApiResponse<AscBuildUploadResponse> {
        val processing = polls++ == 0
        val state = if (processing) AscBuildUploadState("PROCESSING") else finalState
        val build = if (state.state ==
            "COMPLETE"
        ) {
            AscRelationshipItem("builds", "build-1")
        } else {
            null
        }
        return ok(
            AscBuildUploadResponse(
                AscBuildUploadData(
                    "bu1",
                    AscBuildUploadAttributes(state = state),
                    AscBuildUploadRelationships(AscNullableRelationship(build)),
                ),
            ),
        )
    }

    override suspend fun listBetaGroups(token: String, appId: String) = ok(
        AscListResponse(
            listOf(AscResource("g1", "betaGroups", AscBetaGroupAttributes("Testers"))),
        ),
    )

    override suspend fun addBuildToBetaGroups(
        token: String,
        buildId: String,
        groupIds: List<String>,
    ): ApiResponse<Unit> {
        calls += "groups ${groupIds.joinToString()}"
        return ok(Unit)
    }

    override suspend fun listBetaBuildLocalizations(token: String, buildId: String) =
        ok(AscListResponse<AscBetaBuildLocalizationAttributes>(emptyList()))

    override suspend fun createBetaBuildLocalization(
        token: String,
        buildId: String,
        locale: String,
        whatsNew: String,
    ): ApiResponse<AscSingleResponse<AscBetaBuildLocalizationAttributes>> {
        calls += "created $locale"
        return ok(
            AscSingleResponse(
                AscResource(
                    "l1",
                    "betaBuildLocalizations",
                    AscBetaBuildLocalizationAttributes(locale, whatsNew),
                ),
            ),
        )
    }

    override suspend fun submitBetaReview(token: String, buildId: String): ApiResponse<Unit> {
        calls += "betaReview"
        return ok(Unit)
    }

    override suspend fun attachBuildToVersion(
        token: String,
        versionId: String,
        buildId: String,
    ): ApiResponse<Unit> {
        calls += "attach"
        return ok(Unit)
    }

    override suspend fun createReviewSubmission(
        token: String,
        appId: String,
        platform: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> {
        calls += "reviewSubmission"
        return ok(
            AscSingleResponse(
                AscResource("rs1", "reviewSubmissions", AscReviewSubmissionAttributes()),
            ),
        )
    }

    override suspend fun createReviewSubmissionItem(
        token: String,
        reviewSubmissionId: String,
        versionId: String,
    ): ApiResponse<Unit> {
        calls += "reviewItem"
        return ok(Unit)
    }

    override suspend fun submitReviewSubmission(
        token: String,
        reviewSubmissionId: String,
    ): ApiResponse<AscSingleResponse<AscReviewSubmissionAttributes>> {
        calls += "reviewSubmit"
        return ok(
            AscSingleResponse(
                AscResource("rs1", "reviewSubmissions", AscReviewSubmissionAttributes()),
            ),
        )
    }

    // Unused by the binary flow.
    override suspend fun listVersionLocalizations(token: String, versionId: String) =
        notImplemented<AscListResponse<AscLocalizationAttributes>>()
    override suspend fun listScreenshotSets(token: String, localizationId: String) =
        notImplemented<AscListResponse<AscScreenshotSetAttributes>>()
    override suspend fun createScreenshotSet(
        token: String,
        localizationId: String,
        displayType: String,
    ) = notImplemented<AscSingleResponse<AscScreenshotSetAttributes>>()
    override suspend fun listScreenshots(token: String, screenshotSetId: String) =
        notImplemented<AscListResponse<AscScreenshotAttributes>>()
    override suspend fun deleteScreenshot(token: String, screenshotId: String) =
        notImplemented<Unit>()
    override suspend fun reserveScreenshot(
        token: String,
        screenshotSetId: String,
        fileName: String,
        fileSize: Long,
    ) = notImplemented<AscSingleResponse<AscScreenshotAttributes>>()
    override suspend fun commitScreenshot(
        token: String,
        screenshotId: String,
        md5Checksum: String,
    ) = notImplemented<AscSingleResponse<AscScreenshotAttributes>>()
    override suspend fun getScreenshot(token: String, screenshotId: String) =
        notImplemented<AscSingleResponse<AscScreenshotAttributes>>()
    override suspend fun reorderScreenshots(
        token: String,
        screenshotSetId: String,
        screenshotIds: List<String>,
    ) = notImplemented<Unit>()
    override suspend fun updateLocalizationMetadata(
        token: String,
        localizationId: String,
        metadata: AppStoreLocalizedMetadata,
    ) = notImplemented<AscSingleResponse<AscLocalizationAttributes>>()
    override suspend fun listAppInfos(token: String, appId: String) =
        notImplemented<AscListResponse<AscAppInfoAttributes>>()
    override suspend fun listAppInfoLocalizations(token: String, appInfoId: String) =
        notImplemented<AscListResponse<AscAppInfoLocalizationAttributes>>()
    override suspend fun updateAppInfoLocalization(
        token: String,
        appInfoLocalizationId: String,
        name: String?,
        subtitle: String?,
    ) = notImplemented<AscSingleResponse<AscAppInfoLocalizationAttributes>>()
    override suspend fun createAppInfoLocalization(
        token: String,
        appInfoId: String,
        locale: String,
        name: String?,
        subtitle: String?,
    ) = notImplemented<AscSingleResponse<AscAppInfoLocalizationAttributes>>()
    override suspend fun updateBetaBuildLocalization(
        token: String,
        localizationId: String,
        whatsNew: String,
    ) = notImplemented<AscSingleResponse<AscBetaBuildLocalizationAttributes>>()
}
