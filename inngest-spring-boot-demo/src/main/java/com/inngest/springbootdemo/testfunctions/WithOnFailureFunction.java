package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WithOnFailureFunction extends InngestFunction {
    private static final java.util.concurrent.atomic.AtomicInteger onFailureCallCount = new java.util.concurrent.atomic.AtomicInteger(0);

    public static int getOnFailureCallCount() {
        return onFailureCallCount.get();
    }

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
        onFailureCallCount.incrementAndGet();
        return "On Failure Success";
    }
}
