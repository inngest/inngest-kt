package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class ReturnNullStepIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testReturnNullFromStep() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/return.null.step").getIds()[0];

        Thread.sleep(5000);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        assertEquals(eventId, run.getEvent_id());
        assertEquals("Completed", run.getStatus());
        assertNull(run.getOutput());
    }
}
