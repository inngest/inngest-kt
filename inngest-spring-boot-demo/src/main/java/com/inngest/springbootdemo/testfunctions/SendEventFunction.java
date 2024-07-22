package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class SendEventFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("send-fn")
            .name("Send Function")
            .triggerEvent("test/send");
    }

    @Override
    public SendEventsResponse execute(FunctionContext ctx, Step step) {
        return step.sendEvent("send-test", new InngestEvent(
            "test/no-match",
            new HashMap<String, String>()));
    }
}
