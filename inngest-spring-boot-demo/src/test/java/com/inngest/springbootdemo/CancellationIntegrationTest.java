package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class CancellationIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testCancelOnEventOnly() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        String event = InngestFunctionTestHelpers.sendEvent(client, "test/cancelable").getIds()[0];
        devServer.waitForCondition("cancelable function to remain running before cancellation", timeout, () -> {
            EventRunsResponse<Object> runs = devServer.runsByEvent(event);
            return runs != null
                && runs.getData() != null
                && runs.getData().length > 0
                && "Running".equals(runs.first().getStatus())
                && runs.first().getEnded_at() == null;
        });
        InngestFunctionTestHelpers.sendEvent(client, "cancel/cancelable");

        devServer.waitForCondition("cancelable function to be cancelled", timeout, () -> {
            EventRunsResponse<Object> runs = devServer.runsByEvent(event);
            return runs != null
                && runs.getData() != null
                && runs.getData().length > 0
                && "Cancelled".equals(runs.first().getStatus())
                && runs.first().getEnded_at() != null;
        });
        RunEntry<Object> run = devServer.runsByEvent(event).first();
        assertEquals("Cancelled", run.getStatus());
        assertNotNull(run.getEnded_at());
    }

    @Test
    void testCancelOnIf() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        String user23Event = InngestFunctionTestHelpers.sendEvent(client, "test/cancel-on-match", Collections.singletonMap("userId", "23")).getIds()[0];
        String user42Event = InngestFunctionTestHelpers.sendEvent(client, "test/cancel-on-match", Collections.singletonMap("userId", "42")).getIds()[0];
        devServer.waitForCondition("both cancel-on-match runs to start waiting", timeout, () -> {
            EventRunsResponse<Object> user23Runs = devServer.runsByEvent(user23Event);
            EventRunsResponse<Object> user42Runs = devServer.runsByEvent(user42Event);
            return user23Runs != null
                && user23Runs.getData() != null
                && user23Runs.getData().length > 0
                && "Running".equals(user23Runs.first().getStatus())
                && user23Runs.first().getEnded_at() == null
                && user42Runs != null
                && user42Runs.getData() != null
                && user42Runs.getData().length > 0
                && "Running".equals(user42Runs.first().getStatus())
                && user42Runs.first().getEnded_at() == null;
        });
        InngestFunctionTestHelpers.sendEvent(client, "cancel/cancel-on-match", Collections.singletonMap("userId", "42"));

        // Only the event matching the if expression is canceled
        devServer.waitForCondition("matching run to be cancelled", timeout, () -> {
            EventRunsResponse<Object> user42Runs = devServer.runsByEvent(user42Event);
            return user42Runs != null
                && user42Runs.getData() != null
                && user42Runs.getData().length > 0
                && "Cancelled".equals(user42Runs.first().getStatus())
                && user42Runs.first().getEnded_at() != null;
        });
        devServer.waitForCondition("non-matching run to keep waiting", timeout, () -> {
            EventRunsResponse<Object> user23Runs = devServer.runsByEvent(user23Event);
            return user23Runs != null
                && user23Runs.getData() != null
                && user23Runs.getData().length > 0
                && "Running".equals(user23Runs.first().getStatus())
                && user23Runs.first().getEnded_at() == null;
        });

        RunEntry<Object> user23Run = devServer.runsByEvent(user23Event).first();
        RunEntry<Object> cancelledRun = devServer.runsByEvent(user42Event).first();
        assertEquals("Running", user23Run.getStatus());
        assertNull(user23Run.getEnded_at());
        assertEquals("Cancelled", cancelledRun.getStatus());
        assertNotNull(cancelledRun.getEnded_at());
    }
}
