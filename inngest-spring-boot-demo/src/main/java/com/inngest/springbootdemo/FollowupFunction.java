package com.inngest.springbootdemo;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

@FunctionConfig(
    id = "fn-follow-up",
    name = "My follow up function!"
)
@FunctionEventTrigger(event = "user.signup.completed")
@FunctionEventTrigger(event = "random-event")
public class FollowupFunction extends InngestFunction {
    @Override
    public LinkedHashMap<String, Object> execute(@NotNull FunctionContext ctx, @NotNull Step step) {
        System.out.println("-> follow up handler called " + ctx.getEvent().getName());
        return ctx.getEvent().getData();
    }
}
