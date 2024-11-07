package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class StepErrorsIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testShouldCatchStepErrorWhenInvokeThrows() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/invoke.failure").getIds()[0];

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        String output = (String) run.getOutput();

        assertEquals("Completed", run.getStatus() );
        assertNotNull(run.getEnded_at());

        assertEquals("Something fatally went wrong", output);
    }

    @Test
    void testShouldCatchStepErrorWhenRunThrows() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/try.catch.run").getIds()[0];

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        String output = (String) run.getOutput();

        assertEquals("Completed", run.getStatus());
        assertNotNull(run.getEnded_at());

        assertEquals("Something fatally went wrong", output);
    }

    @Test
    void testShouldCatchAndDeserializeExceptionWhenRunThrows() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/try.catch.deserialize.exception").getIds()[0];

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        Object output = run.getOutput();
        if (output instanceof LinkedHashMap) {
            fail("Run threw an exception serialized into a LinkedHashMap:" + output);
        }
        String outputString = (String) output;

        assertEquals("Completed", run.getStatus());
        assertNotNull(run.getEnded_at());

        assertEquals("Something fatally went wrong", outputString);
    }
}
