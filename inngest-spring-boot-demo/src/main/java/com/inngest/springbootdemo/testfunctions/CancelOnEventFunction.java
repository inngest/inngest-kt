package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

public class CancelOnEventFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("cancelable-fn")
            .name("Cancelable Function")
            .cancelOn("cancel/cancelable")
            .triggerEvent("test/cancelable");
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
