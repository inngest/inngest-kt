package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class ErrorsInStepsIntegrationTest {
    @BeforeAll
    static void setup(@Autowired CommHandler handler) {
        handler.register("http://localhost:8080");
    }

    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 10000;

    @Autowired
    private Inngest client;

    @Test
    void testNonRetriableShouldFail() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/non.retriable").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        LinkedHashMap<String, String> output = (LinkedHashMap<String, String>) run.getOutput();

        assertEquals(run.getStatus(), "Failed");
        assertNotNull(run.getEnded_at());
        assert output.get("name").contains("NonRetriableError");
        assert output.get("stack").contains("NonRetriableErrorFunction.lambda$execute");
        assertEquals(output.get("message"), "something fatally went wrong");
    }


    @Test
    void testRetriableShouldSucceedAfterFirstAttempt() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/retriable").first();

        Thread.sleep(5000);

        RunEntry<Object> run1 = devServer.runsByEvent(eventId).first();

        assertEquals(run1.getStatus(), "Running");

        // The second attempt should succeed, so we wait for the second run to finish.
        Thread.sleep(15000);

        RunEntry<Object> run2 = devServer.runsByEvent(eventId).first();

        assertEquals(run2.getStatus(), "Completed");
        assertNotNull(run2.getEnded_at(), "Completed");
        assertNotNull(run2.getOutput(), "Success");
    }


}
