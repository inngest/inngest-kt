package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

@FunctionConfig(id = "non-retriable-fn", name = "NonRetriable Function")
@FunctionEventTrigger(event = "test/non.retriable")
public class NonRetriableErrorFunction extends InngestFunction {

    @Override
    public String execute(FunctionContext ctx, Step step) {
        step.run("fail-step", () -> {
            throw new NonRetriableError("something fatally went wrong");
        }, String.class);

        return "Success";
    }
}
