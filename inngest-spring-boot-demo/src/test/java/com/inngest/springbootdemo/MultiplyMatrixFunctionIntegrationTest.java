package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class MultiplyMatrixFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testShouldMultiplyTwoMatricesAndReturnTheResult() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        ArrayList<ArrayList<Integer>> matrixA = new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(1, 2)),
            new ArrayList<>(Arrays.asList(3, 4))
        ));
        ArrayList<ArrayList<Integer>> matrixB = new ArrayList<>(Arrays.asList(
            new ArrayList<>(Arrays.asList(5, 6)),
            new ArrayList<>(Arrays.asList(7, 8))
        ));

        LinkedHashMap<String, Object> eventData = new LinkedHashMap<String, Object>() {{
            put("matrixA", matrixA);
            put("matrixB", matrixB);
        }};

        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/multiply.matrix", eventData).getIds()[0];
        RunEntry<Object> run = devServer.waitForRunStatus(
            devServer.waitForEventRuns(eventId, timeout).first().getRun_id(),
            "Completed",
            timeout
        );

        assertEquals("Completed", run.getStatus());
        assertEquals(
            new ArrayList<>(Arrays.asList(
                new ArrayList<>(Arrays.asList(19, 22)),
                new ArrayList<>(Arrays.asList(43, 50))
            )),
            run.getOutput()
        );
    }
}
