package com.inngest.signingkey

import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BearerTokenKtTest {
    @Test
    fun `test invalid signing key format`() {
        assertFailsWith<InvalidSigningKeyException> {
            getAuthorizationHeader("signke-prod-blah")
        }
    }

    @Test
    fun `test signing key is not a valid hex string`() {
        assertFailsWith<InvalidSigningKeyException> {
            getAuthorizationHeader("signkey-prod-8fjau3mn")
        }
    }

    @Test
    fun `builds authorization header with bearer token for prod environment`() {
        val authorizationHeader = getAuthorizationHeader("signkey-prod-12345678")
        assertEquals(
            "Authorization",
            authorizationHeader.keys.first(),
        )
        assertEquals(
            "Bearer signkey-prod-b2ed992186a5cb19f6668aade821f502c1d00970dfd0e35128d51bac4649916c",
            authorizationHeader.values.first(),
        )
    }

    @Test
    fun `builds authorization header with bearer token for non prod environment`() {
        val authorizationHeader = getAuthorizationHeader("signkey-test-12345678")
        assertEquals(
            "Authorization",
            authorizationHeader.keys.first(),
        )
        assertEquals(
            "Bearer signkey-test-b2ed992186a5cb19f6668aade821f502c1d00970dfd0e35128d51bac4649916c",
            authorizationHeader.values.first(),
        )
    }
}
