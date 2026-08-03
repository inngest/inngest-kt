package com.inngest.connect.internal

import kotlin.random.Random

/**
 * Reconnect pacing, matching the schedule shared by the JS and Go SDKs.
 */
internal object Backoff {
    private val backoffMillis =
        longArrayOf(
            1_000, 2_000, 5_000, 10_000, 20_000, 30_000, 60_000, 120_000, 300_000,
        )

    /** Delay before reconnect attempt [attempt] (0-based), capped at 5 minutes. */
    fun delayMillis(attempt: Int): Long {
        if (attempt < 0) return backoffMillis.first()
        return backoffMillis[minOf(attempt, backoffMillis.size - 1)]
    }

    /**
     * Fast retry delay used when the start request fails because the chosen
     * gateway is draining: 500-1500ms of jitter instead of the full backoff.
     */
    fun gatewayDrainingRetryMillis(random: Random = Random.Default): Long = 500L + random.nextLong(1_000L)
}
