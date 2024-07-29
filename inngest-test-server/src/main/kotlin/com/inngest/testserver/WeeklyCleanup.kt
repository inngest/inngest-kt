package com.inngest.testserver

import com.inngest.*

/**
 * A demo function that runs on a given cron schedule
 */
class WeeklyCleanup : InngestFunction() {
    override fun config(builder: InngestFunctionConfigBuilder): InngestFunctionConfigBuilder =
        builder
            .id("weekly-cleanup")
            .name("Weekly cleanup")
            .triggerCron("0 0 * * 0") // Every Sunday at midnight

    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {
        // this function will not have an event or events to utilize as it's a triggered on a cron schedule
        // this is ideal for periodic jobs that don't fit an event-trigger pattern.

        return linkedMapOf("hello" to true)
    }
}
