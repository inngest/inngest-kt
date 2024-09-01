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
    ): String {
        val imageURL =
            try {
                step.run("generate-image-dall-e") {
                    // Call the DALL-E model to generate an image
                    throw Exception("Failed to generate image")

                    "example.com/image-dall-e.jpg"
                }
            } catch (e: StepError) {
                // Fall back to a different image generation model
                step.run("generate-image-midjourney") {
                    // Call the MidJourney model to generate an image
                    "example.com/image-midjourney.jpg"
                }
            }

        try {
            step.invoke<Map<String, Any>>(
                "push-to-slack-channel",
                "ktor-dev",
                "PushToSlackChannel",
                mapOf("image" to imageURL),
                null,
            )
        } catch (e: StepError) {
            // Pushing to Slack is not critical, so we can ignore the error, log it
            // or handle it in some other way.
        }

        return imageURL
    }
}
