package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

// This function is not currently being exercised by any integration test,
// but including it here and in DemoTestConfiguration means we are at least testing
// that it can be registered with the inngest dev server properly
public class PriorityFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("PriorityFunction")
            .name("Priority Function")
            .triggerEvent("test/priority")
            .priority("100");
    }

    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        return step.run("result", () -> 42, Integer.class);
    }
}

