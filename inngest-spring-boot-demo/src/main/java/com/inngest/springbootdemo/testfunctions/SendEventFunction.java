package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;

import java.util.HashMap;

@FunctionConfig(id = "send-fn", name = "Send Function")
@FunctionEventTrigger(event = "test/send")
public class SendEventFunction extends InngestFunction {

    @Override
    public SendEventsResponse execute(FunctionContext ctx, Step step) {
        return step.sendEvent("send-test", new InngestEvent(
            "test/no-match",
            new HashMap<String, String>()));
    }
}
