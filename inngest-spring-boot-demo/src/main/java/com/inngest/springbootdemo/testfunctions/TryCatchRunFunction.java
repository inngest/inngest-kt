package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

class CustomException extends RuntimeException {
    public CustomException(String message) {
        super(message);
    }
}

public class TryCatchRunFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("try-catch-run-fn")
            .name("Try catch run")
            .triggerEvent("test/try.catch.run")
            .retries(0);
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        try {
            step.run("fail-step", () -> {
                throw new CustomException("Something fatally went wrong");
            }, String.class);
        } catch (StepError e) {
            return e.getMessage();
        }

        return "An error should have been thrown and this message should not be returned";
    }
}
