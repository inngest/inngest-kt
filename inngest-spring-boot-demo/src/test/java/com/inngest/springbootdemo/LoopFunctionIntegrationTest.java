package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class LoopFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testStepsInLoopExecuteCorrectly() throws Exception {
        String loopEvent = InngestFunctionTestHelpers.sendEvent(client, "test/loop").getIds()[0];
        Thread.sleep(2000);

        RunEntry<Object> loopRun = devServer.runsByEvent(loopEvent).first();
        assertEquals("Completed", loopRun.getStatus());

        assertEquals(170, loopRun.getOutput());
    }
}
