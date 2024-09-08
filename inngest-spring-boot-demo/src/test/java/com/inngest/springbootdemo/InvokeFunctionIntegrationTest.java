package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class InvokeFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testShouldInvokeScaleObjectFunctionAndReturnCorrectResult() throws Exception {
        ArrayList<ArrayList<Integer>> square = new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(1, 1)),
            new ArrayList<>(Arrays.asList(1, 2)),
            new ArrayList<>(Arrays.asList(2, 1)),
            new ArrayList<>(Arrays.asList(2, 2))
        ));

        LinkedHashMap<String, Object> eventData = new LinkedHashMap<String, Object>() {{
            put("matrix", square);
            put("scaleX", 2);
            put("scaleY", 3);
        }};

        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/scale2d.object", eventData).getIds()[0];

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        assertEquals(run.getStatus(), "Completed");

        assertEquals(new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(2, 3)),
            new ArrayList<>(Arrays.asList(2, 6)),
            new ArrayList<>(Arrays.asList(4, 3)),
            new ArrayList<>(Arrays.asList(4, 6))
        )), run.getOutput());
    }
}
