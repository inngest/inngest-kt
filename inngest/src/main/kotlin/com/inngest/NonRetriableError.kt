package com.inngest

open class NonRetriableError
    @JvmOverloads
    constructor(
        message: String,
        cause: Throwable? = null,
    ) : RuntimeException(message, cause)
