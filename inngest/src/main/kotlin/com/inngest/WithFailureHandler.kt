package com.inngest

interface WithFailureHandler {
    /**
     * Function to call when the Inngest function fails.
     *
     * @param ctx The function context including event(s) that triggered the function
     * @param step A class with methods to define steps within the function
     */
    fun onFailure(
        ctx: FunctionContext,
        step: Step,
    ): Any?
}
