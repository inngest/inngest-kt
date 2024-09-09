package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class IdempotentFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("idempotent-fn")
            .name("Idempotent Function")
            .triggerEvent("test/idempotent")
            .idempotency("event.data.companyId");
    }

    @Getter
    private static int counter = 0;
    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        return step.run("increment-count", () -> counter++, Integer.class);
    }
}
