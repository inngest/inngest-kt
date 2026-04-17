package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
@Disabled("Failure-handler system events are not emitted reliably enough in the dev server to gate CI yet.")
@Execution(ExecutionMode.CONCURRENT)
class WithOnFailureIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testWithOnFailureShouldCallOnFailure() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        String eventName = "test/with-on-failure";
        String eventId = InngestFunctionTestHelpers.sendEvent(client, eventName).getIds()[0];

        // Check that the original function failed
        RunEntry<Object> run = devServer.waitForRunStatus(
            devServer.waitForEventRuns(eventId, timeout).first().getRun_id(),
            "Failed",
            timeout
        );
        LinkedHashMap<String, String> output = (LinkedHashMap<String, String>) run.getOutput();

        assertEquals("Failed", run.getStatus());
        assertNotNull(run.getEnded_at());
        assert output.get("name").contains("NonRetriableError");

        // Check that the onFailure function was called
        EventEntry event = devServer.waitForEvent(
            e -> "inngest/function.failed".equals(e.getName()) && eventName.equals(e.getData().getEvent().getName()),
            timeout
        );

        RunEntry<Object> onFailureRun = devServer.waitForRunStatus(
            devServer.waitForEventRuns(event.getInternal_id(), timeout).first().getRun_id(),
            "Completed",
            timeout
        );

        assertEquals("Completed", onFailureRun.getStatus());
        assertEquals("On Failure Success", (String) onFailureRun.getOutput());
    }
}
