package com.inngest.testserver

import com.inngest.*
import java.time.Duration

@FunctionConfig(id = "ProcessAlbum", name = "ProcessAlbum")
@FunctionEventTrigger(event = "delivery/process.requested")
class ProcessAlbum : InngestFunction() {

    //    override val id = "ProcessAlbum"
    override fun config(builder: InngestFunction.Builder): InngestFunction.Builder {
        return builder
            .name("Process Album!")
            .trigger(InngestFunctionTrigger(event = "delivery/process.requested"))
            .batchEvents(30, Duration.ofSeconds(10))
    }

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {

//        val list = ctx.events.map { e -> e.data.get("something") }
//        println(list);

        for (evt in ctx.events) {
//            println(evt);
            // NOTE - App ID is set on the serve level
            val res = step.invoke<Map<String, Any>>(
                "restore-album-${evt.data.get("albumId")}",
                "ktor-dev",
                "RestoreFromGlacier",
                evt.data,
                null,
            )
        }

        return linkedMapOf("hello" to true)
    }

    fun isRestoredFromGlacier(temp: Int): Boolean {
        if (temp > 2) {
            return true
        }
        return false;
    }

    fun restoreFromGlacier(): String {
        return "FILES_RESTORED"
    }
}
