package com.inngest.springbootdemo.testfunctions;

import com.inngest.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class Scale2DObjectFunction extends InngestFunction {
    @NotNull
    @Override
    public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
        return builder
            .id("Scale2DObjectFunction")
            .name("Scale 2D Object")
            .triggerEvent("test/scale2d.object");
    }

    @Override
    public List<List<Double>> execute(FunctionContext ctx, Step step) {
        List<List<Integer>> matrix = (List<List<Integer>>) ctx.getEvent().getData().get("matrix");
        int scaleX = (int) ctx.getEvent().getData().get("scaleX");
        int scaleY = (int) ctx.getEvent().getData().get("scaleY");

        List<List<Integer>> scalingMatrix = new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(scaleX, 0)),
            new ArrayList<>(Arrays.asList(0, scaleY))
        ));

        LinkedHashMap<String, Object> eventData = new LinkedHashMap<String, Object>() {{
            put("matrixA", matrix);
            put("matrixB", scalingMatrix);
        }};

        return step.invoke(
            "multiply-matrix",
            "spring_test_demo",
            "MultiplyMatrixFunction",
            eventData,
            null,
            List.class);
    }
}
