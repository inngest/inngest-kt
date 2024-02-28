package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

@FunctionConfig(id = "no-step-fn", name = "No Step Function")
@FunctionEventTrigger(event = "test/no-step")
public class EmptyStepFunction extends InngestFunction {
    @Override
    public String execute(FunctionContext ctx, Step step) {
        return "hello world";
    }
}
