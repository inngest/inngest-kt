package com.inngest.springbootdemo.testfunctions;

import com.inngest.FunctionContext;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.Step;
import org.jetbrains.annotations.NotNull;

public class LoopFunction extends InngestFunction {

    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("loop-fn")
            .name("Loop Function")
            .triggerEvent("test/loop");
    }


    @Override
    public Integer execute(FunctionContext ctx, Step step) {
        int runningCount = 10;
        for (int i = 0; i < 5; i++) {
            int effectivelyFinalVariableForLambda = runningCount;
            runningCount = step.run("add-ten", () -> effectivelyFinalVariableForLambda + 10, Integer.class);
        }

        return runningCount;
    }
}

