package com.inngest.springbootdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.inngest.InngestHeaderKey;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Value;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
@Execution(ExecutionMode.CONCURRENT)
public class DevModeIntrospectionTest {
    @Value("${local.server.port}")
    private int port;

    @Test
    public void shouldReturnInsecureIntrospectPayload() throws Exception {
        // Exercise the real servlet stack. MockMvc's request-builder signatures
        // are not binary-compatible between Spring 5 and Spring 7.
        Request request = new Request.Builder().url("http://127.0.0.1:" + port + "/api/inngest").build();
        try (Response response = new OkHttpClient().newCall(request).execute()) {
            assertEquals(200, response.code());
            assertEquals("application/json", response.header("Content-Type"));
            assertEquals("springboot", response.header(InngestHeaderKey.Framework.getValue()));
            assertNotNull(response.body());
            JsonNode payload = new ObjectMapper().readTree(response.body().string());
            assertTrue(payload.path("authentication_succeeded").isNull());
            assertTrue(payload.path("function_count").isNumber());
            assertEquals(BooleanNode.FALSE, payload.path("has_event_key"));
            assertEquals(BooleanNode.FALSE, payload.path("has_signing_key"));
            assertEquals("dev", payload.path("mode").textValue());
            assertEquals("2024-05-24", payload.path("schema_version").textValue());
        }
    }
}
