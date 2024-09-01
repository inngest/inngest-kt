package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

public class ZeroRetriesFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("zero-retries-function")
            .name("Zero Retries Function")
            .triggerEvent("test/zero.retries")
            .retries(0);
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        step.run("fail-step", () -> {
            throw new RetryAfterError("This is a retriable exception but retries are set to 0", 50);
        }, String.class);

        // This is unreachable because the step above will always throw an exception, and it will never be retried.
        return "Success";
    }
}
