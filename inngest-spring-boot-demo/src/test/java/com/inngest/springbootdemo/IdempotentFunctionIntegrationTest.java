package com.inngest.springbootdemo;

import com.inngest.Inngest;
import com.inngest.springbootdemo.testfunctions.IdempotentFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.Map;

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
        Map dataPayload = Collections.singletonMap("companyId", 42);
        String eventWithIdempotencyKey = InngestFunctionTestHelpers.sendEvent(client, "test/idempotent", dataPayload).getIds()[0];
        String eventWithSameIdempotencyKey = InngestFunctionTestHelpers.sendEvent(client, "test/idempotent", dataPayload).getIds()[0];

        Thread.sleep(4000);

        // With the same idempotency key, only one of the events should have run
        RunEntry<Object> firstRun = devServer.runsByEvent(eventWithIdempotencyKey).first();
        assertEquals(0, devServer.runsByEvent(eventWithSameIdempotencyKey).data.length);
        assertEquals("Completed", firstRun.getStatus());

        // This would be 2 if the function was not idempotent
        assertEquals(1, IdempotentFunction.getCounter());


        Map differentDataPayload = Collections.singletonMap("companyId", 43);
        String eventWithDifferentIdempotencyKey = InngestFunctionTestHelpers.sendEvent(client, "test/idempotent", differentDataPayload).getIds()[0];

        Thread.sleep(4000);

        // Event with a different idempotency key will run once
        RunEntry<Object> otherRun = devServer.runsByEvent(eventWithDifferentIdempotencyKey).first();
        assertEquals("Completed", otherRun.getStatus());
        assertEquals(2, IdempotentFunction.getCounter());
    }
}
