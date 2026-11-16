package dev.ribod.storestudio.core.publish.appstore

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Native JDK ES256 JWT builder for App Store Connect REST API authentication.
 *
 * Implements ASN.1/DER to IEEE P1363 (JOSE) signature conversion without
 * third-party cryptographic dependencies.
 */
public object AppStoreConnectJwt {

    private const val AUDIENCE: String = "appstoreconnect-v1"
    private const val DEFAULT_EXPIRATION_SECONDS: Long = 1200L

    /**
     * Generates a signed ES256 JWT Bearer token valid for App Store Connect API requests.
     */
    public fun generateToken(
        keyId: String,
        issuerId: String,
        privateKeyPemOrPath: String,
        expirationSeconds: Long = DEFAULT_EXPIRATION_SECONDS,
        nowSeconds: Long = Instant.now().epochSecond,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()

        val headerJson = buildJsonObject {
            put("alg", "ES256")
            put("kid", keyId)
            put("typ", "JWT")
        }.toString()
        val headerB64 = encoder.encodeToString(headerJson.toByteArray(Charsets.UTF_8))

        val claimsJson = buildJsonObject {
            put("iss", issuerId)
            put("iat", nowSeconds)
            put("exp", nowSeconds + expirationSeconds)
            put("aud", AUDIENCE)
        }.toString()
        val claimsB64 = encoder.encodeToString(claimsJson.toByteArray(Charsets.UTF_8))

        val signingInput = "$headerB64.$claimsB64"
        val privateKey = parseEcPrivateKey(privateKeyPemOrPath)

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        val joseSignature = derToJose(signature)
        val signatureB64 = encoder.encodeToString(joseSignature)
        return "$signingInput.$signatureB64"
    }

    /**
     * Parses an EC PKCS#8 private key from PEM string or file path.
     */
    public fun parseEcPrivateKey(rawPemOrPath: String): PrivateKey {
        val trimmed = rawPemOrPath.trim()
        val pemContent = if (trimmed.startsWith("-----BEGIN")) {
            trimmed
        } else {
            val file = File(trimmed)
            if (file.exists()) file.readText() else trimmed
        }

        val cleanKey = pemContent
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN EC PRIVATE KEY-----", "")
            .replace("-----END EC PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\\r", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        val keyBytes = Base64.getDecoder().decode(cleanKey)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("EC").generatePrivate(keySpec)
    }

    /**
     * Converts a standard ASN.1/DER ECDSA signature into the 64-byte IEEE P1363 (JOSE) format.
     */
    internal fun derToJose(der: ByteArray): ByteArray {
        var offset = 0
        require(der[offset++] == 0x30.toByte()) { "Invalid DER sequence header" }
        val totalLen = der[offset++].toInt() and 0xFF
        val actualOffset = if (totalLen and 0x80 != 0) {
            val numLenBytes = totalLen and 0x7F
            offset + numLenBytes
        } else {
            offset
        }
        var ptr = actualOffset

        require(der[ptr++] == 0x02.toByte()) { "Expected integer marker for R coordinate" }
        val rLen = der[ptr++].toInt() and 0xFF
        val rBytes = der.copyOfRange(ptr, ptr + rLen)
        ptr += rLen

        require(der[ptr++] == 0x02.toByte()) { "Expected integer marker for S coordinate" }
        val sLen = der[ptr++].toInt() and 0xFF
        val sBytes = der.copyOfRange(ptr, ptr + sLen)

        val jose = ByteArray(64)
        copyCoordinate(rBytes, jose, 0)
        copyCoordinate(sBytes, jose, 32)
        return jose
    }

    private fun copyCoordinate(src: ByteArray, dest: ByteArray, destOffset: Int) {
        var srcStart = 0
        while (srcStart < src.size && src[srcStart] == 0.toByte() && src.size - srcStart > 32) {
            srcStart++
        }
        val len = src.size - srcStart
        val pad = 32 - len
        require(pad >= 0) { "ECDSA coordinate length exceeds 32 bytes: ${src.size}" }
        System.arraycopy(src, srcStart, dest, destOffset + pad, len)
    }
}

/** Caches an App Store Connect JWT and regenerates it before it expires. */
internal class AppStoreTokens(
    private val keyId: String,
    private val issuerId: String,
    private val privateKey: String,
) {
    private var token = AppStoreConnectJwt.generateToken(keyId, issuerId, privateKey)
    private var tokenAt = System.currentTimeMillis()

    @Synchronized
    fun fresh(): String {
        if (System.currentTimeMillis() - tokenAt > REFRESH_MS) {
            token = AppStoreConnectJwt.generateToken(keyId, issuerId, privateKey)
            tokenAt = System.currentTimeMillis()
        }
        return token
    }

    private companion object {
        const val REFRESH_MS = 15L * 60L * 1000L
    }
}
