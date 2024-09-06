package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class DebouncedFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testDebouncedFunctionExecutesTrailingEdge() throws Exception {
        String firstEvent = InngestFunctionTestHelpers.sendEvent(client, "test/debounced_2_second").getIds()[0];
        String secondEvent = InngestFunctionTestHelpers.sendEvent(client, "test/debounced_2_second").getIds()[0];

        Thread.sleep(4000);

        // With debouncing, the first event is skipped in favor of the second one because they were both sent within
        // the debounce period of 2 seconds
        assertEquals(0, devServer.runsByEvent(firstEvent).data.length);
        RunEntry<Object> secondRun = devServer.runsByEvent(secondEvent).first();

        assertEquals("Completed", secondRun.getStatus());
        assertEquals(42, secondRun.getOutput());
    }
}
