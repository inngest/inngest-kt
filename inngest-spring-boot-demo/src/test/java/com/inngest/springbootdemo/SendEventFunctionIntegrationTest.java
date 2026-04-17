package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class SendEventFunctionIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    @Autowired
    private Inngest client;

    @Test
    void testSendEventFunctionSendsEventSuccessfully() throws Exception {
        Duration timeout = Duration.ofSeconds(20);
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/send").getIds()[0];

        RunEntry<Object> run = devServer.waitForRunStatus(
            devServer.waitForEventRuns(eventId, timeout).first().getRun_id(),
            "Completed",
            timeout
        );

        // Generic nested structures are frustrating to deserialize properly.
        LinkedHashMap<String, ArrayList<String>> output = (LinkedHashMap<String, ArrayList<String>>) run.getOutput();
        ArrayList<String> ids = output.get("ids");
        EventEntry downstreamEvent = devServer.waitForEvent(
            e -> "test/no-match".equals(e.getName()),
            timeout
        );

        assertEquals(run.getStatus(), "Completed");
        assertEquals(1, ids.size());
        assertTrue(
            ids.contains(downstreamEvent.getInternal_id()) || ids.contains(downstreamEvent.getId()),
            String.format(
                "Expected returned downstream event ids %s to include event %s/%s",
                ids,
                downstreamEvent.getInternal_id(),
                downstreamEvent.getId()
            )
        );
    }
}
