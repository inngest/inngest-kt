package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

@FunctionConfig(id = "wait-for-event-fn", name = "Wait for Event Function")
@FunctionEventTrigger(event = "test/wait-for-event")
public class WaitForEventFunction extends InngestFunction {

    @Override
    public String execute(FunctionContext ctx, Step step) {
        Object event = step.waitForEvent("wait-test",
            "test/yolo.wait",
            "8s",
            null);

        return event == null ? "empty" : "fullfilled";
    }
}
