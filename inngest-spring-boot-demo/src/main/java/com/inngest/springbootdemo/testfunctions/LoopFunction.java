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

        int effectivelyFinalVariableForLambda1 = runningCount;
        runningCount = step.run("add-num:3", () -> effectivelyFinalVariableForLambda1 + 50, Integer.class);

        for (int i = 0; i < 5; i++) {
            int effectivelyFinalVariableForLambda2 = runningCount;
            runningCount = step.run("add-num", () -> effectivelyFinalVariableForLambda2 + 10, Integer.class);
        }

        return runningCount;
    }
}

