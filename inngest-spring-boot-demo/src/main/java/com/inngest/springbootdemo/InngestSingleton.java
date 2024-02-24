package com.inngest.springbootdemo;

import com.inngest.*;
import kotlin.jvm.functions.Function2;

import java.time.Duration;
import java.util.HashMap;

// NOTE: We probably don't need this singleton anymore
// when revisiting the SDK's interface.
public class InngestSingleton {
    private static CommHandler instance;

    public static synchronized CommHandler getInstance() {
        if (instance == null) {
            FunctionTrigger fnTrigger = new FunctionTrigger("user-signup", null, null);
            FunctionTrigger[] triggers = {fnTrigger};
            FunctionOptions fnConfig = new FunctionOptions("fn-id-slug", "My function!", triggers);

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

                return new HashMap<String, String>() {{
                    put("message", "cool - this finished running");
                }};
            };
            InngestFunction function = new InngestFunction(fnConfig, handler);

            HashMap<String, InngestFunction> functions = new HashMap<>();
            functions.put("fn-id-slug", function);

            instance = new CommHandler(functions);
        }
        return instance;
    }
}
