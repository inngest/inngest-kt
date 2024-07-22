package com.inngest.springbootdemo;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public class FollowupFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("fn-follow-up")
            .name("My follow up function!")
            .triggerEvent("user.signup.completed")
            .triggerEvent("random-event");
    }

    @Override
    public LinkedHashMap<String, Object> execute(@NotNull FunctionContext ctx, @NotNull Step step) {
        System.out.println("-> follow up handler called " + ctx.getEvent().getName());
        return ctx.getEvent().getData();
    }
}
