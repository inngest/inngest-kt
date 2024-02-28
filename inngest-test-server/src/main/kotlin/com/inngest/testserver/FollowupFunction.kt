package com.inngest.testserver

import com.inngest.*

@FunctionConfig(id = "fn-follow-up", name = "My follow up function!")
@FunctionEventTrigger(event = "user.signup.completed")
@FunctionEventTrigger(event = "random-event")
class FollowupFunction : InngestFunction() {
    override fun execute(
        ctx: FunctionContext,
        step: Step,
    ): LinkedHashMap<String, Any> {
        println("-> follow up handler called " + ctx.event.name)
        return ctx.event.data
    }
}
