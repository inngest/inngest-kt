package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.FunctionOptions;
import com.inngest.FunctionTrigger;
import com.inngest.InngestFunction;
import com.inngest.FunctionContext;
import com.inngest.Step;
import com.inngest.InngestEvent;
import kotlin.jvm.functions.Function2;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;

// NOTE: We probably don't need this singleton anymore
// when revisiting the SDK's interface.
public class InngestSingleton {
    private static CommHandler instance;

    private static final String followUpEvent = "user.signup.completed";

    public static synchronized CommHandler getInstance() {
        if (instance == null) {
            FunctionTrigger fnTrigger = new FunctionTrigger("user-signup", null, null);
            FunctionOptions fnConfig = new FunctionOptions(
                "fn-id-slug",
                "My function!",
                new FunctionTrigger[]{fnTrigger}
            );

            Function2<FunctionContext, Step, HashMap<String, String>> handler = (ctx, step) -> {
                int x = 10;

                System.out.println("-> handler called " + ctx.getEvent().getName());

                int y = step.run("add-ten", () -> x + 10, Integer.class);

                Result res = step.run("cast-to-type-add-ten", () -> {
                    System.out.println("-> running step 1!! " + x);
                    return new Result(y + 10);
                }, Result.class);

                System.out.println("res" + res);
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
            Function2<FunctionContext, Step, LinkedHashMap<String, Object>> followupHandler = (ctx, step) -> {
                System.out.println("-> follow up handler called " + ctx.getEvent().getName());
                return ctx.getEvent().getData();
            };

            HashMap<String, InngestFunction> functions = new HashMap<>();
            functions.put("fn-id-slug", new InngestFunction(fnConfig, handler));
            functions.put("fn-follow-up", new InngestFunction(followupFnConfig, followupHandler));

            instance = new CommHandler(functions);
        }
        return instance;
    }
}
