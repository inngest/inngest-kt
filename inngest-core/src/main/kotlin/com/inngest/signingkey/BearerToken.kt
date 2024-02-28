package com.inngest.signingkey

import com.inngest.RequestHeaders
import java.security.MessageDigest

val SIGNING_KEY_REGEX = Regex("""(?<prefix>^signkey-[\w]+-)(?<key>.*)""")

/**
 * Takes a signing key in the form "signkey-<env>-<key>" and returns "signkey-<env>-<hex-encoded sha256 of key>"
 * Inspired by https://www.baeldung.com/sha-256-hashing-java
 *
 * @param signingKey signing key in the form "signkey-<env>-<key>"
 * @return the hashed signing key in the form "signkey-<env>-<hex-encoded sha256 of key>"
 * @throws InvalidSigningKeyException If signingKey is not in the form "signkey-<env>-<key>"
 */
private fun hashedSigningKey(signingKey: String): String {
    val matchResult = SIGNING_KEY_REGEX.matchEntire(signingKey) ?: throw InvalidSigningKeyException()

    // We aggressively assert non null here because if `matchEntire` had failed (and thus these capture groups didn't
    // exist), we would have already thrown an exception
    val prefix = matchResult.groups["prefix"]!!.value
    val key = matchResult.groups["key"]!!.value

    val sha256MessageDigest = MessageDigest.getInstance("SHA-256") // not thread safe, get new instance every time
    val hashedKey = sha256MessageDigest.digest(key.toByteArray(Charsets.UTF_8))
    // https://gist.github.com/lovubuntu/164b6b9021f5ba54cefc67f60f7a1a25
    val hexEncodedHashedKey = hashedKey.fold(StringBuilder()) { sb, it -> sb.append("%02x".format(it)) }.toString()

    return "$prefix$hexEncodedHashedKey"
}

fun getAuthorizationHeader(signingKey: String): RequestHeaders {
    val hashedSigningKey = hashedSigningKey(signingKey)
    return mapOf("Authorization" to "Bearer $hashedSigningKey")
}
