package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class RateLimitedFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testFunctionIsRateLimited() throws Exception {
        String event1 = InngestFunctionTestHelpers.sendEvent(client, "test/rateLimit").getIds()[0];
        // Rate limit test function is limited to 2 over 6 seconds. Based on the simplistic description of GCRA in
        // https://www.inngest.com/docs/guides/rate-limiting#how-rate-limiting-works
        // we need to sleep at least 3 seconds here for the second event not to get rate limited
        Thread.sleep(3500);
        String event2 = InngestFunctionTestHelpers.sendEvent(client, "test/rateLimit").getIds()[0];
        Thread.sleep(1000);
        String event3 = InngestFunctionTestHelpers.sendEvent(client, "test/rateLimit").getIds()[0];

        // Sleep at least 6 seconds for the rate limit bucket to be completely cleared
        Thread.sleep(6000);

        // Rate limit should only allow the first 2 events to run
        assertEquals("Completed", devServer.runsByEvent(event1).first().getStatus());
        assertEquals("Completed", devServer.runsByEvent(event2).first().getStatus());
        assertEquals(0, devServer.runsByEvent(event3).data.length);

        // new event after the rate limit period will run, but the previously skipped event will stay skipped
        String event4 = InngestFunctionTestHelpers.sendEvent(client, "test/rateLimit").getIds()[0];
        Thread.sleep(4000);

        assertEquals(0, devServer.runsByEvent(event3).data.length);
        assertEquals("Completed", devServer.runsByEvent(event4).first().getStatus());
    }
}
