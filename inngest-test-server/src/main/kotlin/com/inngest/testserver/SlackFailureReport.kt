package com.inngest.testserver

import com.inngest.*

class SlackFailureReport : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("always-fail-fn")
            .name("Always Fail Function")
            .triggerEvent("always-fail-fn")

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): String {
        step.run("throw exception") {
            throw RuntimeException("This function always fails")
            "Step result"
        }

        return "Success"
    }

    override fun onFailure(
        ctx: FunctionContext,
        step: Step,
    ): String {
        step.run("send slack message") {
            "Sending a message to Slack"
        }

        return "onFailure Success"
    }
}
