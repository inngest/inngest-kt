package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

public class EmptyStepFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("no-step-fn")
            .name("No Step Function")
            .triggerEvent("test/no-step");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        return "hello world";
    }
}
