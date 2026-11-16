package dev.ribod.storestudio.core.publish.play

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

/**
 * Pure JDK RS256 JWT builder for authenticating Google Cloud Service Accounts.
 */
public object PlayServiceAccountJwt {

    private val JSON = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val ANDROID_PUBLISHER_SCOPE =
        "https://www.googleapis.com/auth/androidpublisher"

    /**
     * Parses service account credentials from raw JSON string content or a file path.
     */
    public fun parseCredentials(rawJsonOrPath: String): ServiceAccountCredentials {
        val trimmed = rawJsonOrPath.trim()
        val jsonContent = if (trimmed.startsWith("{")) {
            trimmed
        } else {
            val file = File(trimmed)
            if (file.exists()) file.readText() else trimmed
        }
        val credentials = JSON.decodeFromString(ServiceAccountCredentials.serializer(), jsonContent)
        // The JWT is posted to token_uri, so a hostile value would leak a signed assertion
        val uri = java.net.URI(credentials.token_uri)
        val host = uri.host.orEmpty()
        require(
            uri.scheme == "https" &&
                (host == "oauth2.googleapis.com" || host.endsWith(".googleapis.com")),
        ) {
            "token_uri must be an https googleapis.com URL"
        }
        return credentials
    }

    /**
     * Signs and returns an RS256 JWT assertion for OAuth2 token exchange with Google.
     */
    public fun createSignedJwt(
        credentials: ServiceAccountCredentials,
        expirationSeconds: Long = 3600,
        nowSeconds: Long = Instant.now().epochSecond,
    ): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()

        val headerJson = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
        }.toString()
        val headerB64 = encoder.encodeToString(headerJson.toByteArray(Charsets.UTF_8))

        val claimsJson = buildJsonObject {
            put("iss", credentials.client_email)
            put("scope", ANDROID_PUBLISHER_SCOPE)
            put("aud", credentials.token_uri)
            put("exp", nowSeconds + expirationSeconds)
            put("iat", nowSeconds)
        }.toString()
        val claimsB64 = encoder.encodeToString(claimsJson.toByteArray(Charsets.UTF_8))

        val signingInput = "$headerB64.$claimsB64"
        val privateKey = parsePrivateKey(credentials.private_key)

        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(privateKey)
            update(signingInput.toByteArray(Charsets.UTF_8))
        }.sign()

        val signatureB64 = encoder.encodeToString(signature)
        return "$signingInput.$signatureB64"
    }

    private fun parsePrivateKey(pem: String): java.security.PrivateKey {
        val cleanKey = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace("\\n", "")
            .replace("\\r", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()

        val keyBytes = Base64.getDecoder().decode(cleanKey)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }
}
