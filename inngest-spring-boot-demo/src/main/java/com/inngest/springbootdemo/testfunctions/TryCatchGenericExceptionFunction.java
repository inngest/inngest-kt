package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

public class TryCatchGenericExceptionFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("try-catch-deserialize-exception-function")
            .name("Try Catch Deserialize Exception Function")
            .triggerEvent("test/try.catch.deserialize.exception")
            .retries(0);
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        try {
            step.run("fail-step", () -> {
                throw new CustomException("Something fatally went wrong");
            }, String.class);
        } catch (Exception originalException) {
            Exception e = step.run("handle-error", () -> originalException, Exception.class);
            return e.getMessage();
        }

        return "An error should have been thrown and this message should not be returned";
    }
}
