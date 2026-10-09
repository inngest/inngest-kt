package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class SleepFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testSleepFunctionRunningSuccessfully() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/sleep").getIds()[0];

        Thread.sleep(5000);

        String runId = devServer.runsByEvent(eventId).first().getRun_id();
        // Event-run summaries can infer Completed from a child step while the run is sleeping.
        RunEntry<Object> run = devServer.runById(runId, Object.class).getData();

        assertEquals("Running", run.getStatus());
        assertNull(run.getEnded_at());
        assertNull(run.getOutput());

        Thread.sleep(10000);

        RunEntry<Integer> updatedRun = devServer.runById(run.getRun_id(), Integer.class).getData();

        assertEquals(eventId, updatedRun.getEvent_id());
        assertEquals("Completed", updatedRun.getStatus());
        assertNotNull(updatedRun.getEnded_at());
        assertEquals(42, updatedRun.getOutput());
    }
}
