package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class StepErrorsIntegrationTest {
    @BeforeAll
    static void setup(@Autowired CommHandler handler) {
        handler.register("http://localhost:8080");
    }

    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testShouldCatchStepErrorWhenInvokeThrows() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/invoke.failure").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        String output = (String) run.getOutput();

        assertEquals("Completed", run.getStatus() );
        assertNotNull(run.getEnded_at());

        assertEquals("Something fatally went wrong", output);
    }

    @Test
    void testShouldCatchStepErrorWhenRunThrows() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/try.catch.run").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        String output = (String) run.getOutput();

        assertEquals("Completed", run.getStatus());
        assertNotNull(run.getEnded_at());

        assertEquals("Something fatally went wrong", output);
    }

}
