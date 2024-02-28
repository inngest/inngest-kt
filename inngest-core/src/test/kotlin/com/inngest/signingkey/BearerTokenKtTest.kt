package com.inngest.signingkey

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BearerTokenKtTest {
    @Test
    fun `test invalid signing key format`() {
        assertThrows<InvalidSigningKeyException> {
            getAuthorizationHeader("signke-prod-blah")
        }
    }

    @Test
    fun `builds authorization header with bearer token for prod environment`() {
        val authorizationHeader = getAuthorizationHeader("signkey-prod-8fjau3mn")
        assertEquals(
            "Authorization",
            authorizationHeader.keys.first(),
        )
        assertEquals(
            "Bearer signkey-prod-3c8335d113497a3a0b3e6bc18c12bf59e3db1c964c9f66765f374f5f7b473ac7",
            authorizationHeader.values.first(),
        )
    }

    @Test
    fun `builds authorization header with bearer token for non prod environment`() {
        val authorizationHeader = getAuthorizationHeader("signkey-staging-8fjau3mn")
        assertEquals(
            "Authorization",
            authorizationHeader.keys.first(),
        )
        assertEquals(
            "Bearer signkey-staging-3c8335d113497a3a0b3e6bc18c12bf59e3db1c964c9f66765f374f5f7b473ac7",
            authorizationHeader.values.first(),
        )
    }
}
