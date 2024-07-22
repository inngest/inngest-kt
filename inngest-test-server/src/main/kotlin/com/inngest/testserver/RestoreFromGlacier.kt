package com.inngest.testserver

import com.inngest.*
import java.time.Duration

//@FunctionConfig(id = "RestoreFromGlacier", name = "RestoreFromGlacier")
class RestoreFromGlacier : InngestFunction() {

    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder {
        return builder
            .id("RestoreFromGlacier")
            .name("Restore from Glacier")
            .trigger(InngestFunctionTriggers.Event("delivery/restore.requested"))
            .concurrency(10, null, ConcurrencyScope.ENVIRONMENT)
    }

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {

        step.run("restore") {
            if (!isRestoredFromGlacier(0)) {
                restoreFromGlacier()
            }
        }
        var i = 0
        while (i < 6) {
            val isRestored = step.run(String.format("check-status-%d", i)) {
                isRestoredFromGlacier(i)
            }
            if (isRestored) {
                return linkedMapOf("restored" to true)
            }
            step.sleep(String.format("wait-for-restore-%d", i), Duration.ofSeconds(5))
            i++
        }

//        throw NonRetriableError("Could not restore")
        return linkedMapOf("restored" to false)
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
