package com.inngest.testserver

import com.inngest.*
import java.time.Duration

/**
 * A demo function that accepts an event in a batch and invokes a child function
 */
class ProcessAlbum : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("ProcessAlbum")
            .name("Process Album!")
            .triggerEvent("delivery/process.requested")
            .batchEvents(30, Duration.ofSeconds(10))

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {
//        val list = ctx.events.map { e -> e.data.get("something") }
//        println(list);

        for (evt in ctx.events) {
//            println(evt);
            // NOTE - App ID is set on the serve level
            val res =
                step.invoke<Map<String, Any>>(
                    "restore-album-${evt.data["albumId"]}",
                    "ktor-dev",
                    "RestoreFromGlacier",
                    evt.data,
                    null,
                )
            println(res["restored"])
        }

        return linkedMapOf("hello" to true)
    }
}
