package com.inngest.signingkey

import com.inngest.InngestEnv
import com.inngest.ServeConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val HMAC_SHA256 = "HmacSHA256"

// Implementation of this is inspired by the pure Java example from https://www.baeldung.com/java-hmac#hmac-using-jdk-apis
@OptIn(ExperimentalStdlibApi::class)
private fun computeHMAC(
    data: String,
    key: String,
): String {
    val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_SHA256)
    val mac = Mac.getInstance(HMAC_SHA256)
    mac.init(secretKeySpec)
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHexString()
}

internal fun signRequest(
    requestBody: String,
    timestamp: Long,
    signingKey: String,
): String = signRequest(requestBody, timestamp.toString(), signingKey)

private fun signRequest(
    requestBody: String,
    timestamp: String,
    signingKey: String,
): String {
    val matchResult = SIGNING_KEY_REGEX.matchEntire(signingKey) ?: throw InvalidSigningKeyException()
    val key = matchResult.groups["key"]!!.value
    val message = requestBody + timestamp

    return computeHMAC(message, key)
}

class InvalidSignatureHeaderException(
    message: String,
) : Throwable(message)

class ExpiredSignatureHeaderException : Throwable("signature header has expired")

const val FIVE_MINUTES_IN_SECONDS = 5L * 60

internal fun validateSignature(
    signatureHeader: String,
    signingKey: String,
    requestBody: String,
) {
    // TODO: Find a way to parse signatureHeader as URL params without constructing a full URL
    val dummyUrl = "https://test.inngest.com/?$signatureHeader"
    val url =
        dummyUrl.toHttpUrlOrNull()
            ?: throw InvalidSignatureHeaderException("signature header does not match expected format")
    val timestamp =
        url.queryParameter("t")?.toLongOrNull()
            ?: throw InvalidSignatureHeaderException("timestamp is invalid")
    val signature = url.queryParameter("s") ?: throw InvalidSignatureHeaderException("signature is invalid")

    val fiveMinutesAgo = Instant.now().minusSeconds(FIVE_MINUTES_IN_SECONDS).epochSecond
    if (timestamp < fiveMinutesAgo) {
        throw ExpiredSignatureHeaderException()
    }

    val actualSignature = signRequest(requestBody, timestamp, signingKey)
    if (actualSignature != signature) {
        throw InvalidSignatureHeaderException("signature is invalid")
    }
}

/**
 * A function to check if the signature header is valid for a given body. This function completes
 * with Unit if everything is valid, otherwise it'll throw a relevant exception
 *
 * @param signatureHeader The `X-Inngest-Signature` header in the format "t=<seconds_since_unix_epoch>&s=<signature>"
 * @param requestBody The request body as a string
 * @param serverKind The `X-Inngest-Server-Kind` header, either "dev" or "cloud"
 * @param config The current ServeConfig instance, holds relevant environment configuration
 */
fun checkHeadersAndValidateSignature(
    signatureHeader: String?,
    requestBody: String,
    serverKind: String?,
    config: ServeConfig,
) {
    val useDevServer = config.client.env == InngestEnv.Dev

    // exit early without checking signature if we are using dev server
    if (useDevServer) {
        if (serverKind != "dev") {
            // TODO: Use a real logger
            println("WARNING: using dev server but received X-Inngest-Server-Kind: $serverKind")
        }
        return
    }

    val signingKey = config.signingKey()

    signatureHeader
        ?: throw InvalidSignatureHeaderException("Using cloud inngest but did not receive X-Inngest-Signature")

    validateSignature(signatureHeader, signingKey, requestBody)
}
