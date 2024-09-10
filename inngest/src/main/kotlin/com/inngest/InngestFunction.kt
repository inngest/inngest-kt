package com.inngest

const val FUNCTION_FAILED = "inngest/function.failed"

abstract class InngestFunction {
    open fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder = builder

    /**
     * The function handler that will be run whenever the function is executed.
     *
     * @param ctx The function context including event(s) that triggered the function
     * @param step A class with methods to define steps within the function
     */
    abstract fun execute(
        ctx: FunctionContext,
        step: Step,
    ): Any?

    private fun buildConfig(): InngestFunctionConfigBuilder {
        val builder = InngestFunctionConfigBuilder()
        return this.config(builder)
    }

    fun id(): String = buildConfig().id!!

    internal fun toInngestFunction(): InternalInngestFunction {
        val builder = InngestFunctionConfigBuilder()
        val configBuilder = this.config(builder)
        return InternalInngestFunction(configBuilder, this::execute)
    }

    internal fun toFailureHandler(appId: String): InternalInngestFunction? {
        val onFailureMethod = this.javaClass.getMethod("onFailure", FunctionContext::class.java, Step::class.java)

        if (onFailureMethod.declaringClass != InngestFunction::class.java) {
            val fnConfig = buildConfig()
            val fullyQualifiedId = "$appId-${fnConfig.id}"
            val fnName = fnConfig.name ?: fnConfig.id

            val configBuilder =
                InngestFunctionConfigBuilder()
                    .id("${fnConfig.id}-failure")
                    .name("$fnName (failure)")
                    .triggerEventIf(FUNCTION_FAILED, "event.data.function_id == '$fullyQualifiedId'")

            return InternalInngestFunction(configBuilder, this::onFailure)
        }
        return null
    }

    /**
     * Provide a function to be called if your function fails, meaning
     * that it ran out of retries and was unable to complete successfully.
     *
     * This is useful for sending warning notifications or cleaning up
     * after a failure and supports all the same functionality as a
     * regular handler.
     *
     * @param ctx The function context including event(s) that triggered the function
     * @param step A class with methods to define steps within the function
     */
    open fun onFailure(
        ctx: FunctionContext,
        step: Step,
    ): Any? = null
}
