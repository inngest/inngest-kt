package com.inngest

import com.fasterxml.jackson.annotation.JsonProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

data class DummyClass(
    @JsonProperty("sum")
    val sum: Int,
)

internal class StateTest {
    @Test
    fun testGetHashedId() {
        val state = State("{}")
        val hashedId = state.getHashFromId("add-ten")
        assertEquals("a5b1e458ee54a384e87fff1486df43e9b3e0c4b8", hashedId)
    }

    @Test
    fun testNoExistingState() {
        val json =
            """
            {
                "steps": {
                    "a5b1e458ee54a384e87fff1486df43e9b3e0c4b8": {
                        "data": 20
                    }
                }
            }
            """.trimIndent()
        val state = State(json)
        val hashedId = state.getHashFromId("something-not-in-state")
        assertFailsWith<StateNotFound> {
            state.getState<Int>(hashedId)
        }
    }

    @Test
    fun testGetIntState() {
        val json =
            """
            {
                "steps": {
                    "a5b1e458ee54a384e87fff1486df43e9b3e0c4b8": {
                        "data": 20
                    }
                }
            }
            """.trimIndent()
        val state = State(json)
        val hashedId = state.getHashFromId("add-ten")
        val stepState = state.getState<Int>(hashedId)
        assertEquals(20, stepState, "state value should be fetched correctly")
    }

    @Test
    fun testGetStateAsClass() {
        val json =
            """
            {
                "steps": {
                    "a5b1e458ee54a384e87fff1486df43e9b3e0c4b8": {
                        "data": { "sum": 30 }
                    }
                }
            }
            """.trimIndent()
        val state = State(json)
        val hashedId = state.getHashFromId("add-ten")
        val stepState = state.getState<DummyClass>(hashedId)
        assertNotNull(stepState)
        assertEquals(30, stepState.sum, "state value should be correctly deserialized")
    }
}
