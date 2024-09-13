package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;


public class CancelOnMatchFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("cancel-on-match-fn")
            .name("Cancel On Match Function")
            .cancelOn("cancel/cancel-on-match", "event.data.userId == async.data.userId")
            .triggerEvent("test/cancel-on-match");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        step.waitForEvent("wait-forever",
            "test/waiting-for-godot",
            "10m",
            null);

        return "I didn't get canceled";
    }
}
