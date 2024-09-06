package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public class DebouncedFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("DebouncedFunction")
            .name("Debounced Function")
            .triggerEvent("test/debounced_2_second")
            .debounce(Duration.ofSeconds(2));
    }

    private static int answer = 42;

    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        return step.run("result", () -> answer++, Integer.class);
    }
}

