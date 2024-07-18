package com.inngest.testserver

import com.inngest.*
import java.time.Duration

@FunctionConfig(id = "ProcessAlbum", name = "ProcessAlbum")
@FunctionEventTrigger(event = "delivery/process.requested")
class ProcessAlbum : InngestFunction() {
    // override required
//    override val id = "ProcessAlbum"
    override fun config(builder: Builder): Builder {
        return builder
            .name("Process Album!")
            .batchEvents(30, Duration.ofSeconds(30))
    }

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {

        // NOTE - App ID is set on the serve level
        val res = step.invoke<Map<String, Any>>(
            "restore-album",
            "ktor-dev",
            "RestoreFromGlacier",
            mapOf("some-arg" to "awesome"),
            null,

            )

//        throw NonRetriableError("Could not restore")
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
