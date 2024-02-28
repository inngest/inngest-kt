package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class CustomStepResultIntegrationTest {
    @BeforeAll
    static void setup(@Autowired CommHandler handler) {
        handler.register();
    }

    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;


    @Test
    void testMultiStepsFunctionWithClassResultStep() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/custom.result.step").first();

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        RunEntry<Result> runWithOutput = devServer.runById(run.getRun_id(), Result.class).getData();

        assertEquals(runWithOutput.getStatus(), "Completed");
        assertEquals(runWithOutput.getOutput().getSum(), (new Result(5).getSum()));
    }
}
