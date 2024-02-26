package com.inngest.springbootdemo;


import com.inngest.*;
import com.inngest.springboot.InngestConfiguration;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.time.Duration;
import java.util.HashMap;
import java.util.function.BiFunction;

@Configuration
public class DemoConfiguration extends InngestConfiguration {

    @Override
    public HashMap<String, InngestFunction> functions() {
        String followUpEvent = "user.signup.completed";
        FunctionTrigger fnTrigger = new FunctionTrigger("user-signup", null, null);
        FunctionTrigger[] triggers = {fnTrigger};
        FunctionOptions fnConfig = new FunctionOptions("fn-id-slug", "My function!", triggers);

        BiFunction<FunctionContext, Step, HashMap<String, String>> handler = (ctx, step) -> {
            int x = 10;

            System.out.println("-> handler called " + ctx.getEvent().getName());

            int y = step.run("add-ten", () -> x + 10, Integer.class);

            Result res = step.run("cast-to-type-add-ten", () -> {
                System.out.println("-> running step 1!! " + x);
                return new Result(y + 10);
            }, Result.class);

            System.out.println("res" + res);

            step.waitForEvent("wait-for-hello", "hello", "10m", null);

            int add = step.run("add-one-hundred", () -> {
                System.out.println("-> running step 2 :) " + (res != null ? res.sum : ""));
                return (res != null ? res.sum : 0) + 100;
            }, Integer.class);

            step.sleep("wait-one-sec", Duration.ofSeconds(2));

            step.run("last-step", () -> (res != null ? res.sum : 0) * add, Integer.class);

            HashMap<String, String> data = new HashMap<String, String>() {{
                put("hello", "world");
            }};
            step.sendEvent("followup-event-id", new InngestEvent(followUpEvent, data));

            return new HashMap<String, String>() {{
                put("message", "cool - this finished running");
            }};
        };

        FunctionTrigger followupFnTrigger = new FunctionTrigger(followUpEvent, null, null);
        FunctionOptions followupFnConfig = new FunctionOptions(
            "fn-follow-up",
            "Follow up function!",
            new FunctionTrigger[]{followupFnTrigger}
        );
        BiFunction<FunctionContext, Step, LinkedHashMap<String, Object>> followupHandler = (ctx, step) -> {
            System.out.println("-> follow up handler called " + ctx.getEvent().getName());
            return ctx.getEvent().getData();
        };

        HashMap<String, InngestFunction> functions = new HashMap<>();
        functions.put("fn-id-slug", new InngestFunction(fnConfig, handler));
        functions.put("fn-follow-up", new InngestFunction(followupFnConfig, followupHandler));

        return functions;
    }

    @Override
    public Inngest inngestClient() {
        return new Inngest("spring_demo");
    }
}

