package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import com.inngest.SendEventsResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;


@IntegrationTest
@Import(DemoTestConfiguration.class)
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class InngestFunctionExecutionIntegrationTest {


    @BeforeAll
    static void setup(@Autowired CommHandler handler) {
        handler.register();
    }

    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testNoStepFunctionRunningSuccessfully() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/no-step").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        assertEquals(run.getStatus(), "Completed");
        assertEquals(run.getOutput(), "hello world");
    }


    @Test
    void testSleepFunctionRunningSuccessfully() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/sleep").first();

        Thread.sleep(5000);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        assertEquals(run.getStatus(), "Running");
        assertNull(run.getEnded_at());

        Thread.sleep(10000);

        RunEntry<Integer> updatedRun = devServer.<Integer>runById(run.getRun_id()).getData();

        assertEquals(updatedRun.getEvent_id(), eventId);
        assertEquals(updatedRun.getStatus(), "Completed");
        assertEquals(updatedRun.getOutput(), 42);
    }

    @Test
    void testTwoStepsFunctionValidResult() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/two.steps").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        assertEquals(run.getStatus(), "Completed");
        assertEquals(run.getOutput(), 5);
    }

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

        RunEntry<Object> updatedRun = devServer.runById(run.getRun_id()).getData();

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

        RunEntry<String> updatedRun = devServer.<String>runById(run.getRun_id()).getData();

        assertEquals(updatedRun.getEvent_id(), eventId);
        assertEquals(updatedRun.getRun_id(), run.getRun_id());
        assertEquals(updatedRun.getStatus(), "Completed");
        assertEquals(updatedRun.getOutput(), "empty");
    }

    @Test
    void testSendEventFunctionSendsEventSuccessfully() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/send").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        // Generic nested structures are frustrating to deserialize properly.
        LinkedHashMap<String, ArrayList<String>> output = (LinkedHashMap<String, ArrayList<String>>) run.getOutput();
        ArrayList<String> ids = output.get("ids");

        assertEquals(run.getStatus(), "Completed");
        assert !ids.isEmpty();
    }
}
