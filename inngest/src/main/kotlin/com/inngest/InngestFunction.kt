package com.inngest

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

    fun id(): String {
        return buildConfig().id!!
    }

    internal fun toInngestFunction(): InternalInngestFunction {
        val builder = InngestFunctionConfigBuilder()
        val configBuilder = this.config(builder)
        return InternalInngestFunction(configBuilder, this::execute)
    }

    // TODO - Add toFailureHandler method to generate a second function if configured
}
