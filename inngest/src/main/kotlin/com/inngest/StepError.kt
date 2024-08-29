package com.inngest

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Wraps user-space errors that occurred during the execution of a step.
 *
 * @param message The user-space error message
 * @param name The name of the user-space error
 * @param stack The original stack trace of the user-space error
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class StepError
    @JvmOverloads
    constructor(
        message: String,
        val name: String = "",
        val stack: String = "",
    ) : RuntimeException(message)
