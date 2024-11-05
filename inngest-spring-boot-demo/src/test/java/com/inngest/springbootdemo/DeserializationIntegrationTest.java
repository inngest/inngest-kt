package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
class DeserializationIntegrationTest {
    @Autowired
    private DevServerComponent devServer;

    static int sleepTime = 5000;

    @Autowired
    private Inngest client;

    @Test
    void testShouldDeserializeSubclassCorrectly() throws Exception {
        String eventId = InngestFunctionTestHelpers.sendEvent(client, "test/deserialize.subclass").getIds()[0];

        Thread.sleep(sleepTime);

        RunEntry<Object> run = devServer.runsByEvent(eventId).first();
        String output = (String) run.getOutput();

        assertEquals("Completed", run.getStatus() );
        assertNotNull(run.getEnded_at());

        assertEquals("Successfully cast Corgi", output);
    }
}
