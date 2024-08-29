package com.inngest.testserver

import com.inngest.*

class PushToSlackChannel : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("PushToSlackChannel")
            .name("Push to Slack Channel")
            .triggerEvent("media/image.generated")

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): String =
        step.run("push-to-slack-channel") {
            // Call Slack API to push the image to a channel
            throw NonRetriableError("Failed to push image to Slack channel ${ctx.event.data["image"]}")

            "Image pushed to Slack channel"
        }
}
