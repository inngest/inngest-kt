package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class CancellationIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testCancelOnEventOnly() throws Exception {
        String event = InngestFunctionTestHelpers.sendEvent(client, "test/cancelable").getIds()[0];
        Thread.sleep(1000);
        InngestFunctionTestHelpers.sendEvent(client, "cancel/cancelable");
        Thread.sleep(1000);

        RunEntry<Object> run = devServer.runsByEvent(event).first();

        assertEquals("Cancelled", run.getStatus());
    }

    @Test
    void testCancelOnIf() throws Exception {
        String user23Event = InngestFunctionTestHelpers.sendEvent(client, "test/cancel-on-match", Collections.singletonMap("userId", "23")).getIds()[0];
        String user42Event = InngestFunctionTestHelpers.sendEvent(client, "test/cancel-on-match", Collections.singletonMap("userId", "42")).getIds()[0];
        Thread.sleep(1000);
        InngestFunctionTestHelpers.sendEvent(client, "cancel/cancel-on-match", Collections.singletonMap("userId", "42"));
        Thread.sleep(1000);

        // Only the event matching the if expression is canceled
        assertEquals("Running", devServer.runsByEvent(user23Event).first().getStatus());
        assertEquals("Cancelled", devServer.runsByEvent(user42Event).first().getStatus());
    }
}
