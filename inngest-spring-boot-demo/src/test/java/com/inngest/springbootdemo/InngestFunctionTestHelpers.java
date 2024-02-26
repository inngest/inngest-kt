package com.inngest.springbootdemo;

import com.inngest.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.BiFunction;

public class InngestFunctionTestHelpers {

    static SendEventResponse sendEvent(Inngest inngest, String eventName) {
        InngestEvent event = new InngestEvent(eventName, new HashMap<String, String>());
        SendEventResponse response = inngest.send(event, SendEventResponse.class);

        assert Objects.requireNonNull(response).ids.length > 0;
        return response;
    }

    static InngestFunction emptyStepFunction() {
        FunctionTrigger fnTrigger = new FunctionTrigger("test/no-step", null, null);
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("no-step-fn", "No Step Function", triggers);

        BiFunction<FunctionContext, Step, String> handler = (ctx, step) -> "hello world";

        return new InngestFunction(fnConfig, handler);
    }

    static InngestFunction sleepStepFunction() {
        FunctionTrigger fnTrigger = new FunctionTrigger("test/sleep", null, null);
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("sleep-fn", "Sleep Function", triggers);

        BiFunction<FunctionContext, Step, Integer> handler = (ctx, step) -> {
            int result = step.run("num", () -> 42, Integer.class);
            step.sleep("wait-one-sec", Duration.ofSeconds(9));

            return result;
        };

        return new InngestFunction(fnConfig, handler);
    }
}
