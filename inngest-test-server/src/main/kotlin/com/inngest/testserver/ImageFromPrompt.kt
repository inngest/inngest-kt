package com.inngest.testserver

import com.inngest.*

class ImageFromPrompt : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("ImageFromPrompt")
            .name("Image from Prompt")
            .triggerEvent("media/prompt.created")

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): String? =
        step.run("generate-image-dall-e") {
            null as String?
        }
}
