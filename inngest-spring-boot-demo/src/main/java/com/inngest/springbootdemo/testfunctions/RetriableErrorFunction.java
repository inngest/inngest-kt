package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

public class RetriableErrorFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("retriable-fn")
            .name("Retriable Function")
            .triggerEvent("test/retriable");
    }

    static int retryCount = 0;

    @Override
    public String execute(FunctionContext ctx, Step step) {
        retryCount++;
        step.run("retriable-step", () -> {
            if (retryCount < 2) {
                throw new RetryAfterError("something went wrong", 10000);
            }
            return "Success";
        }, String.class);

        return "Success";
    }
}
