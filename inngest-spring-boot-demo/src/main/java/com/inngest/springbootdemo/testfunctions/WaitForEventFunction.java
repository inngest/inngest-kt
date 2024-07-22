package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

public class WaitForEventFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("wait-for-event-fn")
            .name("Wait for Event Function")
            .triggerEvent("test/wait-for-event");
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        Object event = step.waitForEvent("wait-test",
            "test/yolo.wait",
            "8s",
            null);

        return event == null ? "empty" : "fullfilled";
    }
}
