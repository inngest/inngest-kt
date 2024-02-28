package com.inngest

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

open class RetryAfterError
    @JvmOverloads
    constructor(message: String, retryAfter: Any, cause: Throwable? = null) :
    RuntimeException(message, cause) {
        var retryAfter: String =
            when (retryAfter) {
                is ZonedDateTime -> retryAfter.format(DateTimeFormatter.ISO_INSTANT)

                is Int -> (retryAfter / 1000).toString()

                // TODO: Add ms parsing: https://github.com/vercel/ms
                is String -> (retryAfter.toInt() / 1000).toString()

                else -> throw IllegalArgumentException("Invalid retryAfter type: ${retryAfter::class.simpleName}")
            }
    }
