package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class WithOnFailureIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testWithOnFailureShouldCallOnFailure() throws Exception {
        String eventName = "test/with-on-failure";
        String eventId = InngestFunctionTestHelpers.sendEvent(client, eventName).getIds()[0];

        Thread.sleep(sleepTime);

        // Check that the original function failed
        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        LinkedHashMap<String, String> output = (LinkedHashMap<String, String>) run.getOutput();

        assertEquals("Failed", run.getStatus());
        assertNotNull(run.getEnded_at());
        assert output.get("name").contains("NonRetriableError");

        // Check that the onFailure function was called
        Optional<EventEntry> event = Arrays.stream(devServer.listEvents().getData())
            .filter(e -> "inngest/function.failed".equals(e.getName()) && eventName.equals(e.getData().getEvent().getName()))
            .findFirst();

        assert event.isPresent();

        RunEntry<Object> onFailureRun = devServer.runsByEvent(event.get().getInternal_id()).first();

        assertEquals("Completed", onFailureRun.getStatus());
        assertEquals("On Failure Success", (String) onFailureRun.getOutput());
    }
}
