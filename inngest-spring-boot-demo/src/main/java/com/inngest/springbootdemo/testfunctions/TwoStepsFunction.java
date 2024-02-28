package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

@FunctionConfig(id = "two-steps-fn", name = "Two Steps Function")
@FunctionEventTrigger(event = "test/two.steps")
public class TwoStepsFunction extends InngestFunction {

    private final int count = 0;

    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        int step1 = step.run("step1", () -> count + 1, Integer.class);
        int tmp1 = step1 + 1;

        int step2 = step.run("step2", () -> tmp1 + 1, Integer.class);
        int tmp2 = step2 + 1;

        return tmp2 + 1;
    }
}

