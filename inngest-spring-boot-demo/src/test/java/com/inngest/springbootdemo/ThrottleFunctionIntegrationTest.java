package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class ThrottleFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testThrottledFunctionShouldNotRunConcurrently() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        String firstEvent = InngestFunctionTestHelpers.sendEvent(client, "test/throttled").getIds()[0];
        Thread.sleep(500);
        String secondEvent = InngestFunctionTestHelpers.sendEvent(client, "test/throttled").getIds()[0];

        RunEntry<Object> firstRun = devServer.waitForRunStatus(
            devServer.waitForEventRuns(firstEvent, timeout).first().getRun_id(),
            "Completed",
            timeout
        );
        RunEntry<Object> secondRun = devServer.waitForRunStatus(
            devServer.waitForEventRuns(secondEvent, timeout).first().getRun_id(),
            "Completed",
            timeout
        );
        assertEquals("Completed", firstRun.getStatus());
        assertEquals("Completed", secondRun.getStatus());
        assertTrue(
            Duration.between(Instant.parse(firstRun.getEnded_at()), Instant.parse(secondRun.getEnded_at())).toMillis() >= 8000,
            "Expected throttling to delay the second run completion by at least 8 seconds"
        );
    }
}
