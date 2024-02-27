package com.inngest.signingkey

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

const val HMAC_SHA256 = "HmacSHA256"

// Implementation of this is inspired by the pure Java example from https://www.baeldung.com/java-hmac#hmac-using-jdk-apis
@OptIn(ExperimentalStdlibApi::class)
private fun computeHMAC(
    algorithm: String,
    data: String,
    key: String,
): String {
    val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm)
    val mac = Mac.getInstance(algorithm)
    mac.init(secretKeySpec)
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toHexString()
}

internal fun signRequest(
    requestBody: String,
    timestamp: Long,
    signingKey: String,
): String {
    return signRequest(requestBody, timestamp.toString(), signingKey)
}

private fun signRequest(
    requestBody: String,
    timestamp: String,
    signingKey: String,
): String {
    val matchResult = SIGNING_KEY_REGEX.matchEntire(signingKey) ?: throw InvalidSigningKeyException()
    val key = matchResult.groups["key"]!!.value
    val message = requestBody + timestamp

    return computeHMAC(HMAC_SHA256, message, key)
}

class InvalidSignatureHeaderException(message: String) : Throwable(message)

class ExpiredSignatureHeaderException : Throwable("signature header has expired")

const val FIVE_MINUTES_IN_SECONDS = 5L * 60

fun validateSignature(
    signatureHeader: String,
    signingKey: String,
    requestBody: String,
): Boolean {
    // TODO: Find a way to parse signatureHeader as URL params without constructing a full URL
    val dummyUrl = "https://test.inngest.com/?$signatureHeader"
    val url = dummyUrl.toHttpUrlOrNull() ?: throw InvalidSignatureHeaderException("signature header does not match expected format")
    val timestamp = url.queryParameter("t")?.toLongOrNull() ?: throw InvalidSignatureHeaderException("timestamp is invalid")
    val signature = url.queryParameter("s") ?: throw InvalidSignatureHeaderException("signature is invalid")

    val fiveMinutesAgo = Instant.now().minusSeconds(FIVE_MINUTES_IN_SECONDS).epochSecond
    if (timestamp < fiveMinutesAgo) {
        throw ExpiredSignatureHeaderException()
    }

    val actualSignature = signRequest(requestBody, timestamp, signingKey)
    return actualSignature == signature
}
