package com.inngest.signingkey

import com.inngest.RequestHeaders
import java.lang.NumberFormatException
import java.security.MessageDigest

val SIGNING_KEY_REGEX = Regex("""(?<prefix>^signkey-[\w]+-)(?<key>.*)""")

/**
 * Takes a signing key in the form "signkey-<env>-<hex-encoded key>" and returns "signkey-<env>-<hex-encoded sha256 of key>"
 * Inspired by https://www.baeldung.com/sha-256-hashing-java
 *
 * @param signingKey signing key in the form "signkey-<env>-<hex-encoded key>"
 * @return the hashed signing key in the form "signkey-<env>-<hex-encoded sha256 of key>"
 * @throws InvalidSigningKeyException If signingKey is not in the form "signkey-<env>-<key>"
 */
@OptIn(ExperimentalStdlibApi::class)
private fun hashedSigningKey(signingKey: String): String {
    val matchResult = SIGNING_KEY_REGEX.matchEntire(signingKey) ?: throw InvalidSigningKeyException()

    // We aggressively assert non null here because if `matchEntire` had failed (and thus these capture groups didn't
    // exist), we would have already thrown an exception
    val prefix = matchResult.groups["prefix"]!!.value
    val key = matchResult.groups["key"]!!.value

    val sha256MessageDigest = MessageDigest.getInstance("SHA-256") // not thread safe, get new instance every time
    val hashedKey =
        try {
            sha256MessageDigest.digest(key.hexToByteArray())
        } catch (e: NumberFormatException) {
            throw InvalidSigningKeyException()
        }
    val hexEncodedHashedKey = hashedKey.toHexString()

    return "$prefix$hexEncodedHashedKey"
}

fun getAuthorizationHeader(signingKey: String): RequestHeaders {
    val hashedSigningKey = hashedSigningKey(signingKey)
    return mapOf("Authorization" to "Bearer $hashedSigningKey")
}
