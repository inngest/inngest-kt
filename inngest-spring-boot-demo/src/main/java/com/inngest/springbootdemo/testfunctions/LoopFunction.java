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

        // explicitly naming a step that the SDK will try to use in the loop shouldn't break the loop
        int effectivelyFinalVariableForLambda1 = runningCount;
        runningCount = step.run("add-num:3", () -> effectivelyFinalVariableForLambda1 + 50, Integer.class);

        for (int i = 0; i < 5; i++) {
            int effectivelyFinalVariableForLambda2 = runningCount;
            // The actual stepIds used will be add-num, add-num:1, add-num:2, add-num:4, add-num:5
            runningCount = step.run("add-num", () -> effectivelyFinalVariableForLambda2 + 10, Integer.class);
        }

        // explicitly reusing step names that the SDK used during the loop should both execute
        // These will be modified to add-num:4:1 and add-num:4:2 respectively
        int effectivelyFinalVariableForLambda3 = runningCount;
        runningCount = step.run("add-num:4", () -> effectivelyFinalVariableForLambda3 + 30, Integer.class);
        int effectivelyFinalVariableForLambda4 = runningCount;
        runningCount = step.run("add-num:4", () -> effectivelyFinalVariableForLambda4 + 30, Integer.class);

        return runningCount;
    }
}

