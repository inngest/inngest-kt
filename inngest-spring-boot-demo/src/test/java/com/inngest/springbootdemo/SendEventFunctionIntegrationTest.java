package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class SendEventFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testSendEventFunctionSendsEventSuccessfully() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/send").getIds()[0];

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();

        // Generic nested structures are frustrating to deserialize properly.
        LinkedHashMap<String, ArrayList<String>> output = (LinkedHashMap<String, ArrayList<String>>) run.getOutput();
        ArrayList<String> ids = output.get("ids");

        assertEquals(run.getStatus(), "Completed");
        assert !ids.isEmpty();
    }
}
