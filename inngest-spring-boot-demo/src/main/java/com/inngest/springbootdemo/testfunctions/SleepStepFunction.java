package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class SleepStepFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("sleep-fn")
            .name("Sleep Function")
            .triggerEvent("test/sleep");
    }

    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        int result = step.run("num", () -> 42, Integer.class);
        step.sleep("wait-one-sec", Duration.ofSeconds(9));

        return result;
    }
}
