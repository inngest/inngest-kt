package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WithOnFailureFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("with-on-failure-function")
            .name("With On Failure Function")
            .triggerEvent("test/with-on-failure");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        step.run("fail-step", () -> {
            throw new NonRetriableError("something fatally went wrong");
        }, String.class);

        return "Success";
    }

    @Nullable
    @Override
    public String onFailure(@NotNull FunctionContext ctx, @NotNull Step step) {
        return "On Failure Success";
    }
}
