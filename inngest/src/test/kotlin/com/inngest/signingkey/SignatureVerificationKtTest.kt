package com.inngest.signingkey

import com.inngest.Inngest
import com.inngest.ServeConfig
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SignatureVerificationKtTest {
    val testBody = "hey!  if you're reading this come work with us: careers@inngest.com"
    val testKey = "signkey-test-12345678"

    @Test
    fun `signRequest produces same signatures with different prefix keys`() {
        val ts = 1709026298L
        val a = signRequest(testBody, ts, "signkey-test-12345678")
        val b = signRequest(testBody, ts, "signkey-prod-12345678")
        val c = signRequest(testBody, ts, "signkey-staging-12345678")

        assertEquals("3f1c811920eb25da7fa70e3ac484e32e93f01dbbca7c9ce2365f2062a3e10c26", a)
        assertEquals(a, b)
        assertEquals(a, c)
    }

    @Test
    fun `fails with an invalid signature header`() {
        assertFailsWith<InvalidSignatureHeaderException> {
            validateSignature("lol", testKey, testBody)
        }
    }

    @Test
    fun `fails with an invalid timestamp`() {
        assertFailsWith<InvalidSignatureHeaderException> {
            validateSignature("t=what&s=yea", testKey, testBody)
        }
    }

    @Test
    fun `fails with an expired timestamp`() {
        val now = Instant.now().epochSecond
        val oneHourAgo = now - 60 * 60
        assertFailsWith<ExpiredSignatureHeaderException> {
            validateSignature("t=$oneHourAgo&s=yea", testKey, testBody)
        }
    }

    @Test
    fun `fails with a signing key that wasn't the one used to create the signature`() {
        val now = Instant.now().epochSecond
        val signature = signRequest(testBody, now, testKey)

        assertFailsWith<InvalidSignatureHeaderException> {
            validateSignature("t=$now&s=$signature", "signkey-test-badkey", testBody)
        }
    }

    @Test
    fun `validateSignature succeeds if signature matches and timestamp is within a reasonable time`() {
        val now = Instant.now().epochSecond
        val signature = signRequest(testBody, now, testKey)

        assertDoesNotThrow {
            validateSignature("t=$now&s=$signature", testKey, testBody)
        }
    }

    @Test
    fun `succeeds without signing key or signature if in dev`() {
        val testConfig =
            ServeConfig(
                Inngest("unit-test", env = "dev"),
                signingKey = null,
            )
        assertDoesNotThrow {
            checkHeadersAndValidateSignature(
                null,
                testBody,
                null,
                testConfig,
            )
        }
    }

    @Test
    fun `fails if in prod and signing key is missing`() {
        val testConfig =
            ServeConfig(
                Inngest("unit-test", env = "prod"),
                signingKey = null,
            )
        val exception =
            assertFailsWith<Exception> {
                checkHeadersAndValidateSignature(
                    null,
                    testBody,
                    null,
                    testConfig,
                )
            }
        assertEquals("signing key is required", exception.message)
    }

    @Test
    fun `fails if in prod with signing key but signature is missing`() {
        val testConfig =
            ServeConfig(
                Inngest("unit-test", env = "prod"),
                signingKey = testKey,
            )
        assertFailsWith<InvalidSignatureHeaderException> {
            checkHeadersAndValidateSignature(
                null,
                testBody,
                null,
                testConfig,
            )
        }
    }

    @Test
    fun `checkHeadersAndValidateSignature succeeds if signature matches and timestamp is within a reasonable time`() {
        val testConfig =
            ServeConfig(
                Inngest("unit-test", env = "prod"),
                signingKey = testKey,
            )

        val now = Instant.now().epochSecond
        val signature = signRequest(testBody, now, testKey)

        assertDoesNotThrow {
            checkHeadersAndValidateSignature(
                "t=$now&s=$signature",
                testBody,
                null,
                testConfig,
            )
        }
    }
}
