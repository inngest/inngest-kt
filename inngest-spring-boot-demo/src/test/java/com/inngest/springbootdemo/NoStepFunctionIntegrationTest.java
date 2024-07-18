package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class NoStepFunctionIntegrationTest {
    @BeforeAll
    static void setup(@Autowired CommHandler handler) {
        handler.register("http://localhost:8080");
    }

    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testNoStepFunctionRunningSuccessfully() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/no-step").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        assertEquals(run.getStatus(), "Completed");
        assertEquals(run.getOutput(), "hello world");
    }
}
