package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public class InvokeFailureFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("invoke-failure-fn")
            .name("Invoke Function")
            .triggerEvent("test/invoke.failure");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        try {
            step.invoke(
                "failing-function",
                "spring_test_demo",
                "non-retriable-fn",
                new LinkedHashMap<String,
                    String>(),
                null,
                Object.class);
        } catch (StepError e) {
            return e.getMessage();
        }

        return "An error should have been thrown and this message should not be returned";
    }
}
