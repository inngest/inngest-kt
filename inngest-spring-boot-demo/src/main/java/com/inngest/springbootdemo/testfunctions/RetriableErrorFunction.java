package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

@FunctionConfig(id = "retriable-fn", name = "Retriable Function")
@FunctionEventTrigger(event = "test/retriable")
public class RetriableErrorFunction extends InngestFunction {
    static int retryCount = 0;

    @Override
    public String execute(FunctionContext ctx, Step step) {
        retryCount++;
        step.run("retriable-step", () -> {
            if (retryCount < 2) {
                throw new RetryAfterError("something went wrong", 10000);
            }
            return "Success";
        }, String.class);

        return "Success";
    }
}
