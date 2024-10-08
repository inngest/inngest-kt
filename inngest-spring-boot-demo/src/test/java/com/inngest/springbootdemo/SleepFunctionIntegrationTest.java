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

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        assertEquals(run.getStatus(), "Running");
        assertNull(run.getEnded_at());

        Thread.sleep(10000);

        RunEntry<Integer> updatedRun = devServer.runById(run.getRun_id(), Integer.class).getData();

        assertEquals(updatedRun.getEvent_id(), eventId);
        assertEquals(updatedRun.getStatus(), "Completed");
        assertEquals(updatedRun.getOutput(), 42);
    }
}
