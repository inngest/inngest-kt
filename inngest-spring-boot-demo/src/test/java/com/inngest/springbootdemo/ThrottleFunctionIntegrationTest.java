package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class ThrottleFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testThrottledFunctionShouldNotRunConcurrently() throws Exception {
        String firstEvent = InngestFunctionTestHelpers.sendEvent(client, "test/throttled").getIds()[0];
        Thread.sleep(500);
        String secondEvent = InngestFunctionTestHelpers.sendEvent(client, "test/throttled").getIds()[0];

        Thread.sleep(5000);

        // Without throttling, both events would have been completed by now
        RunEntry<Object> firstRun = devServer.runsByEvent(firstEvent).first();
        RunEntry<Object> secondRun = devServer.runsByEvent(secondEvent).first();
        assertEquals("Completed", firstRun.getStatus());
        assertEquals("Running", secondRun.getStatus());

        Thread.sleep(10000);

        RunEntry<Object> secondRunAfterWait = devServer.runsByEvent(secondEvent).first();
        assertEquals("Completed", secondRunAfterWait.getStatus());
    }
}
