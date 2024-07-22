package com.inngest.springbootdemo;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.HashMap;

public class UserSignupFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("fn-id-slug")
            .name("My Function!")
            .triggerEvent("user-signup");
    }

    @Override
    public HashMap<String, String> execute(@NotNull FunctionContext ctx, @NotNull Step step) {
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
        step.sendEvent("followup-event-id", new InngestEvent("user.signup.completed", data));

        return new HashMap<String, String>() {{
            put("message", "cool - this finished running");
        }};
    }
}
