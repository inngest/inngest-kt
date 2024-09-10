package com.inngest.testserver

import com.inngest.*
import java.time.Duration

class RestoreFromGlacier : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("RestoreFromGlacier")
            .name("Restore from Glacier")
            .trigger(InngestFunctionTriggers.Event("delivery/restore.requested"))
            .concurrency(10, "event.data.user_id", ConcurrencyScope.ENVIRONMENT)

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
            val isRestored =
                step.run(String.format("check-status-%d", i)) {
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

    // NOTE - This method is only a stub meant to simulate that Glacier restoration will return false
    // the first couple of times in the loop. This is just to show the concept.
    private fun isRestoredFromGlacier(temp: Int): Boolean = temp > 2

    private fun restoreFromGlacier(): String = "FILES_RESTORED"
}
