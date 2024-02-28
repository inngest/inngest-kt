package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

import java.time.Duration;

@FunctionConfig(id = "sleep-fn", name = "Sleep Function")
@FunctionEventTrigger(event = "test/sleep")
public class SleepStepFunction extends InngestFunction {
    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        int result = step.run("num", () -> 42, Integer.class);
        step.sleep("wait-one-sec", Duration.ofSeconds(9));

        return result;
    }
}
