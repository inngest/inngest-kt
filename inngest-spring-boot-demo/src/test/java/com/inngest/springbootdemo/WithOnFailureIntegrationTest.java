package com.inngest.springbootdemo;

import com.inngest.Inngest;
import com.inngest.springbootdemo.testfunctions.WithOnFailureFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
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
        devServer.waitForCondition("onFailure handler to be called", timeout, () -> WithOnFailureFunction.getOnFailureCallCount() >= 1);
        assertEquals(1, WithOnFailureFunction.getOnFailureCallCount());
    }
}
