package com.inngest.springboot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inngest.CommHandler;
import com.inngest.FunctionContext;
import com.inngest.Inngest;
import com.inngest.InngestFunction;
import com.inngest.InngestFunctionConfigBuilder;
import com.inngest.InngestHeaderKey;
import com.inngest.ServeConfig;
import com.inngest.Step;
import com.inngest.SupportedFrameworkName;
import com.inngest.testing.ProtocolFixtures;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class InngestControllerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HashMap<String, InngestFunction> FUNCTIONS = new HashMap<>();

    static {
        FUNCTIONS.put("echo-fn", new EchoFunction());
    }

    private MockMvc mockMvc;

    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() {
        TestController controller = new TestController();
        Inngest client = new Inngest("test-app", null, "evt-key", null, true);
        controller.commHandler = new CommHandler(FUNCTIONS, client, new ServeConfig(client), SupportedFrameworkName.SpringBoot);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("INNGEST_API_BASE_URL");
        if (mockWebServer != null) {
            mockWebServer.shutdown();
            mockWebServer = null;
        }
    }

    @Test
    void postRouteReturnsCallResponseWithRequiredHeaders() throws Exception {
        String responseBody = mockMvc.perform(post("/api/inngest")
                .queryParam("fnId", "echo-fn")
                .contentType("application/json")
                .content(ProtocolFixtures.executionRequestPayloadJson("echo-fn")))
            .andExpect(status().isOk())
            .andExpect(header().string(InngestHeaderKey.RequestVersion.getValue(), "2"))
            .andExpect(header().string(InngestHeaderKey.Framework.getValue(), "springboot"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        assertEquals("\"done\"", responseBody);
    }

    @Test
    void putRouteReturnsSuccessfulSyncResponseAndForwardsExpectedServerKind() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.enqueue(new MockResponse().setBody("{\"ok\":true,\"modified\":true}"));
        mockWebServer.start();
        System.setProperty(
            "INNGEST_API_BASE_URL",
            mockWebServer.url("").toString().substring(0, mockWebServer.url("").toString().length() - 1)
        );

        String responseBody = mockMvc.perform(put("/api/inngest")
                .header("Host", "localhost:8080")
                .header(InngestHeaderKey.ServerKind.getValue(), "cloud")
                .param("deployId", "deploy-1"))
            .andExpect(status().isOk())
            .andExpect(header().string(InngestHeaderKey.RequestVersion.getValue(), "2"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode body = MAPPER.readTree(responseBody);
        RecordedRequest recordedRequest = mockWebServer.takeRequest();

        assertEquals("Successfully synced.", body.get("message").asText());
        assertTrue(body.get("modified").asBoolean());
        assertEquals("/fn/register?deployId=deploy-1", recordedRequest.getPath());
        assertEquals("cloud", recordedRequest.getHeader(InngestHeaderKey.ExpectedServerKind.getValue()));
        assertEquals("2", recordedRequest.getHeader(InngestHeaderKey.RequestVersion.getValue()));
    }

    @Test
    void putRouteReturnsSyncFailurePayloadWhenRegisterFails() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.enqueue(new MockResponse().setResponseCode(400).setBody("{\"error\":\"invalid config\"}"));
        mockWebServer.start();
        System.setProperty(
            "INNGEST_API_BASE_URL",
            mockWebServer.url("").toString().substring(0, mockWebServer.url("").toString().length() - 1)
        );

        String responseBody = mockMvc.perform(put("/api/inngest")
                .header("Host", "localhost:8080"))
            .andExpect(status().isInternalServerError())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode body = MAPPER.readTree(responseBody);
        assertEquals("invalid config", body.get("message").asText());
        assertEquals(false, body.get("modified").asBoolean());
    }

    @RestController
    @RequestMapping("/api/inngest")
    static class TestController extends InngestController {
    }

    static class EchoFunction extends InngestFunction {
        @Override
        public InngestFunctionConfigBuilder config(InngestFunctionConfigBuilder builder) {
            return builder.id("echo-fn");
        }

        @Override
        public Object execute(FunctionContext ctx, Step step) {
            return "done";
        }
    }
}
