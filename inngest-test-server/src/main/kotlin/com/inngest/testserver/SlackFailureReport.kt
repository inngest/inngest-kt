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
        step.run<String>("throw exception") {
            if (ctx.event.data["forceSuccess"] == true) {
                "Step result"
            } else {
                throw RuntimeException("This function always fails")
            }
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
