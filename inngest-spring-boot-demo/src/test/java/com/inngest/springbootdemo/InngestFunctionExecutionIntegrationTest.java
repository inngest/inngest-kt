package com.inngest.springbootdemo;

import com.inngest.CommHandler;
import com.inngest.Inngest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.IfProfileValue;


import static org.junit.jupiter.api.Assertions.assertEquals;


@IntegrationTest
@Import(DemoTestConfiguration.class)
@AutoConfigureMockMvc
@Execution(ExecutionMode.CONCURRENT)
class InngestFunctionExecutionIntegrationTest {


    @BeforeAll
    static void setup(@Autowired CommHandler handler) {
        handler.register();
    }

    @Autowired
    private DevServerComponent devServerComponent;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testNoStepFunctionRunningSuccessfully() throws Exception {
        SendEventResponse response = InngestFunctionTestHelpers.sendEvent(client, "test/no-step");

        Thread.sleep(sleepTime);

        assert response.ids.length > 0;

        RunIdsResponse runId = devServerComponent.runIds(response.ids[0]);
        assertEquals(runId.first().getStatus(), "Completed");
        assertEquals(runId.first().getOutput(), "hello world");
    }
}
