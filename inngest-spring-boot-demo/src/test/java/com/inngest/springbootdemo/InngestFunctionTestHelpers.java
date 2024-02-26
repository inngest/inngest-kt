package com.inngest.springbootdemo;

import com.inngest.*;

import java.util.HashMap;
import java.util.function.BiFunction;

public class InngestFunctionTestHelpers {

    static SendEventResponse sendEvent(Inngest inngest, String eventName) {
        InngestEvent event = new InngestEvent(eventName, new HashMap<String, String>());
        return inngest.send(event, SendEventResponse.class);
    }

    static InngestFunction emptyStepFunction() {
        FunctionTrigger fnTrigger = new FunctionTrigger("test/no-step", null, null);
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("no-step-fn", "No Step Function", triggers);

        BiFunction<FunctionContext, Step, String> handler = (ctx, step) -> "hello world";

        return new InngestFunction(fnConfig, handler);
    }
}
