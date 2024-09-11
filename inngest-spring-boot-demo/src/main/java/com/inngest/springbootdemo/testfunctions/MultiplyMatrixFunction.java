package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class MultiplyMatrixFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("MultiplyMatrixFunction")
            .name("Multiply Matrix")
            .triggerEvent("test/multiply.matrix");
    }


    @Override
    public List<List<Double>> execute(FunctionContext ctx, Step step) {
        List<List<Integer>> A = (List<List<Integer>>) ctx.getEvent().getData().get("matrixA");
        List<List<Integer>> B = (List<List<Integer>>) ctx.getEvent().getData().get("matrixB");

        return step.run("multiply-matrix", () ->
        {
            List<List<Double>> result = new ArrayList<>();
            for (List<Integer> integers : A) {
                List<Double> row = new ArrayList<>();
                for (int j = 0; j < B.get(0).size(); j++) {
                    double sum = 0;
                    for (int k = 0; k < integers.size(); k++) {
                        sum += integers.get(k) * B.get(k).get(j);
                    }
                    row.add(sum);
                }
                result.add(row);
            }
            return result;
        }, List.class);
    }
}
