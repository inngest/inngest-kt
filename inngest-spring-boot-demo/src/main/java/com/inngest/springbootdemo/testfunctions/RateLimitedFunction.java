package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class RateLimitedFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("RateLimitedFunction")
            .name("RateLimited Function")
            .triggerEvent("test/rateLimit")
            .rateLimit(2, Duration.ofSeconds(6));
    }

    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        return step.run("result", () -> 42, Integer.class);
    }
}

