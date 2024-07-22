package com.inngest.testserver

import com.inngest.FunctionContext
import com.inngest.InngestFunction
import com.inngest.InngestFunctionConfigBuilder
import com.inngest.Step

class TranscodeVideo : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("process-video")
            .name("Process video upload")
            .triggerEvent("media/video.uploaded")
            .concurrency(10)

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): HashMap<String, Any> {
        val transcription =
            step.run("transcribe-video") {
                // Download video, run through transcription model, return output
                "Hi there, My name is Jamie..." // dummy example content
            }

        val summary =
            step.run("summarize") {
                // Send t
                "Hi there, My name is Jamie..." // dummy example content
            }

        step.run("save-results") {
            // Save summary, to your database
            // database.save(event.data["videoId"], transcription, summary)
        }

        return hashMapOf("restored" to false)
    }
}
