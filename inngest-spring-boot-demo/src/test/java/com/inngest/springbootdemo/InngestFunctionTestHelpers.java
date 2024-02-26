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
        FunctionTrigger fnTrigger = new FunctionTrigger("test/no-step");
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("no-step-fn", "No Step Function", triggers);

        BiFunction<FunctionContext, Step, String> handler = (ctx, step) -> "hello world";

        return new InngestFunction(fnConfig, handler);
    }

    static InngestFunction sleepStepFunction() {
        FunctionTrigger fnTrigger = new FunctionTrigger("test/sleep");
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("sleep-fn", "Sleep Function", triggers);

        BiFunction<FunctionContext, Step, Integer> handler = (ctx, step) -> {
            int result = step.run("num", () -> 42, Integer.class);
            step.sleep("wait-one-sec", Duration.ofSeconds(9));

            return result;
        };

        return new InngestFunction(fnConfig, handler);
    }

    static InngestFunction twoStepsFunction() {
        FunctionTrigger fnTrigger = new FunctionTrigger("test/two.steps");
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("two-steps-fn", "Two Steps Function", triggers);

        int count = 0;

        BiFunction<FunctionContext, Step, Integer> handler = (ctx, step) -> {
            int step1 = step.run("step1", () -> count + 1, Integer.class);
            int tmp1 = step1 + 1;

            int step2 = step.run("step2", () -> tmp1 + 1, Integer.class);
            int tmp2 = step2 + 1;

            return tmp2 + 1;
        };

        return new InngestFunction(fnConfig, handler);
    }

    static InngestFunction waitForEventFunction() {
        FunctionTrigger fnTrigger = new FunctionTrigger("test/wait-for-event");
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("wait-for-event-fn", "Wait for Event Function", triggers);

        BiFunction<FunctionContext, Step, String> handler = (ctx, step) -> {
            Object event = step.waitForEvent("wait-test", "test/yolo.wait", "8s", null);

            return event == null ? "empty" : "fullfilled";
        };

        return new InngestFunction(fnConfig, handler);
    }

}
