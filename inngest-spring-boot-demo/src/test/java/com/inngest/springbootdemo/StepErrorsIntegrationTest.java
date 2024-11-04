package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
        // If we aren't deserializing subclasses correctly, this run.getOutput will be a hash map of the Jackson exception and so we'll get this
        // java.lang.ClassCastException: class java.util.LinkedHashMap cannot be cast to class java.lang.String (java.util.LinkedHashMap and java.lang.String are in module java.base of loader 'bootstrap')
        String output = (String) run.getOutput();

        assertEquals("Completed", run.getStatus());
        assertNotNull(run.getEnded_at());

        assertEquals("Something fatally went wrong", output);
    }
}
