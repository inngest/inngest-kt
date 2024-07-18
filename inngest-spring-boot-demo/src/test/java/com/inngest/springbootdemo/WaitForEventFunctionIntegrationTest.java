package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class WaitForEventFunctionIntegrationTest {

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
    void testWaitForEventFunctionWhenFullFilled() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/wait-for-event").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        // It should still be waiting for the expected event.
        assertEquals(run.getStatus(), "Running");
        assertNull(run.getEnded_at());

        InngestFunctionTestHelpers.sendEvent(client, "test/yolo.wait").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> updatedRun = devServer.runById(run.getRun_id(), Object.class).getData();

        assertEquals(updatedRun.getEvent_id(), eventId);
        assertEquals(updatedRun.getRun_id(), run.getRun_id());
        assertEquals(updatedRun.getStatus(), "Completed");
        assertEquals(updatedRun.getOutput(), "fullfilled");
    }

    @Test
    void testWaitForEventFunctionWhenTimeOut() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/wait-for-event").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        // It should still be waiting for the expected event.
        assertEquals(run.getStatus(), "Running");
        assertNull(run.getEnded_at());

        Thread.sleep(sleepTime);

        RunEntry<String> updatedRun = devServer.runById(run.getRun_id(), String.class).getData();

        assertEquals(updatedRun.getEvent_id(), eventId);
        assertEquals(updatedRun.getRun_id(), run.getRun_id());
        assertEquals(updatedRun.getStatus(), "Completed");
        assertEquals(updatedRun.getOutput(), "empty");
    }

}
