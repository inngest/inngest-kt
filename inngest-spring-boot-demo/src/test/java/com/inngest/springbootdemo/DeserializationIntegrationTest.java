package com.inngest.springbootdemo;

import com.inngest.Inngest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

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
        Object output = run.getOutput();
        if (output instanceof LinkedHashMap) {
            fail("Run threw an exception serialized into a LinkedHashMap:" + output);
        }
        String outputString = (String) output;

        assertEquals("Completed", run.getStatus() );
        assertNotNull(run.getEnded_at());

        assertEquals("Successfully cast Corgi", outputString);
    }
}
