package com.inngest.springbootdemo;

import com.inngest.Inngest;
import com.inngest.springbootdemo.testfunctions.IdempotentFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Map;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class IdempotentFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testIdempotencyKey() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        Map dataPayload = Collections.singletonMap("companyId", 42);
        String eventWithIdempotencyKey = InngestFunctionTestHelpers.sendEvent(client, "test/idempotent", dataPayload).getIds()[0];
        String eventWithSameIdempotencyKey = InngestFunctionTestHelpers.sendEvent(client, "test/idempotent", dataPayload).getIds()[0];

        // With the same idempotency key, exactly one of the duplicate events should execute.
        devServer.waitForCondition("exactly one duplicate idempotent event to execute", timeout, () -> {
            int firstRunCount = runCount(devServer.runsByEvent(eventWithIdempotencyKey));
            int secondRunCount = runCount(devServer.runsByEvent(eventWithSameIdempotencyKey));
            return IdempotentFunction.getCounter() == 1
                && firstRunCount + secondRunCount == 1;
        });
        RunEntry<Object> firstRun = completedRunForEitherDuplicate(
            eventWithIdempotencyKey,
            eventWithSameIdempotencyKey,
            timeout
        );
        assertEquals("Completed", firstRun.getStatus());

        // This would be 2 if the function was not idempotent
        assertEquals(1, IdempotentFunction.getCounter());

        Map differentDataPayload = Collections.singletonMap("companyId", 43);
        String eventWithDifferentIdempotencyKey = InngestFunctionTestHelpers.sendEvent(client, "test/idempotent", differentDataPayload).getIds()[0];

        // Event with a different idempotency key will run once
        RunEntry<Object> otherRun = devServer.waitForRunStatus(
            devServer.waitForEventRuns(eventWithDifferentIdempotencyKey, timeout).first().getRun_id(),
            "Completed",
            timeout
        );
        devServer.waitForCondition("distinct idempotency key to increment the counter", timeout, () -> IdempotentFunction.getCounter() == 2);
        assertEquals("Completed", otherRun.getStatus());
        assertEquals(2, IdempotentFunction.getCounter());
    }

    private RunEntry<Object> completedRunForEitherDuplicate(
        String firstEventId,
        String secondEventId,
        Duration timeout
    ) throws Exception {
        EventRunsResponse<Object> firstRuns = devServer.runsByEvent(firstEventId);
        if (runCount(firstRuns) > 0) {
            return devServer.waitForRunStatus(firstRuns.first().getRun_id(), "Completed", timeout);
        }

        EventRunsResponse<Object> secondRuns = devServer.runsByEvent(secondEventId);
        if (runCount(secondRuns) > 0) {
            return devServer.waitForRunStatus(secondRuns.first().getRun_id(), "Completed", timeout);
        }

        throw new AssertionError("Expected one idempotent duplicate event to have a run");
    }

    private int runCount(EventRunsResponse<Object> runs) {
        if (runs == null || runs.getData() == null) {
            return 0;
        }

        return runs.getData().length;
    }
}
