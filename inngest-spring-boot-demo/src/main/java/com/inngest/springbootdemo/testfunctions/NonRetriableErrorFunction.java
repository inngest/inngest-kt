package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

public class NonRetriableErrorFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("non-retriable-fn")
            .name("NonRetriable Function")
            .triggerEvent("test/non.retriable");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        step.run("fail-step", () -> {
            throw new NonRetriableError("something fatally went wrong");
        }, String.class);

        return "Success";
    }
}
