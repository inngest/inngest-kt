package com.inngest

internal data class RetryDecision(
    val shouldRetry: Boolean,
    val headers: Map<String, String>,
) {
    companion object {
        internal fun fromException(exception: Exception): RetryDecision =
            when (exception) {
                is RetryAfterError ->
                    RetryDecision(
                        true,
                        mapOf(InngestHeaderKey.RetryAfter.value to exception.retryAfter, noRetryFalse),
                    )

                is NonRetriableError -> RetryDecision(false, mapOf(InngestHeaderKey.NoRetry.value to "true"))

                // Any other error should have the default retry behavior.
                else -> RetryDecision(true, mapOf(noRetryFalse))
            }
    }
}

private val noRetryFalse = InngestHeaderKey.NoRetry.value to "false"
