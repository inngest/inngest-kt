package com.inngest.springbootdemo.testfunctions;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

class Dog {
    @JsonProperty("legs")
    public int legs;

    public Dog(@JsonProperty("legs") int legs) {
        this.legs = legs;
    }
}

class Corgi extends Dog {
    @JsonProperty("stumpy")
    public boolean stumpy;

    public Corgi(@JsonProperty("legs") int legs, @JsonProperty("stumpy") boolean stumpy) {
        super(legs);

        this.stumpy = stumpy;
    }
}

public class DeserializeSubclassFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("DeserializeSubclassFunction")
            .name("Deserialize subclass function")
            .triggerEvent("test/deserialize.subclass")
            .retries(0);
    }

    @Override
    public String execute(FunctionContext ctx, Step step) {
        Dog corgi = step.run("get-corgi", () -> new Corgi(4, true), Dog.class);

        assert(((Corgi) corgi).stumpy == true);

        return "Successfully cast Corgi";
    }
}
