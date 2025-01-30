package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

public class ReturnNullStepFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("null-step-fn")
            .name("Function that has a step that returns null")
            .triggerEvent("test/return.null.step");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        return step.run("null", () -> null, String.class);
    }
}
