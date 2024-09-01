package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class ThrottledFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("ThrottledFunction")
            .name("Throttled Function")
            .triggerEvent("test/throttled")
            .throttle(1, Duration.ofSeconds(10), "throttled", 1);
    }

    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        return step.run("result", () -> 42, Integer.class);
    }
}

