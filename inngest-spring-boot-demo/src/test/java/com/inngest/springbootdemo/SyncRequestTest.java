package com.inngest.springbootdemo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inngest.*;
import com.inngest.springboot.InngestConfiguration;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@ExtendWith(SystemStubsExtension.class)
public class SyncRequestTest {
    static class SyncInngestConfiguration extends InngestConfiguration {
        protected HashMap<String, InngestFunction> functions() {
            return new HashMap<>();
        }

        @Override
        protected Inngest inngestClient() {
            return new Inngest("spring_test_registration");
        }

        @Override
        protected ServeConfig serve(Inngest client) {
            return new ServeConfig(client);
        }

        @Bean
        protected CommHandler commHandler(@Autowired Inngest inngestClient) {
            ServeConfig serveConfig = new ServeConfig(inngestClient);
            return new CommHandler(functions(), inngestClient, serveConfig, SupportedFrameworkName.SpringBoot);
        }
    }

    @SystemStub
    private static EnvironmentVariables environmentVariables;

    public static MockWebServer mockWebServer;

    @Import(SyncInngestConfiguration.class)
    @WebMvcTest(DemoController.class)
    @Nested
    @EnabledIfSystemProperty(named = "test-group", matches = "unit-test")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class InnerSpringTest {
        @Autowired
        private MockMvc mockMvc;

        @BeforeEach
        void BeforeEach() throws Exception {
            mockWebServer = new MockWebServer();
            mockWebServer.start();

            String serverUrl = mockWebServer.url("").toString();

            // Remove the trailing slash from the serverUrl
            serverUrl = serverUrl.substring(0, serverUrl.length() - 1);

            environmentVariables.set("INNGEST_API_BASE_URL", serverUrl);
        }

        @AfterEach
        void afterEach() throws Exception {
            mockWebServer.shutdown();
        }

        private void assertThatPayloadDoesNotContainDeployId(RecordedRequest recordedRequest) throws Exception {
            // The url in the sync payload should not contain the deployId.
            // https://github.com/inngest/inngest/blob/main/docs/SDK_SPEC.md#432-syncing
            String requestBody = recordedRequest.getBody().readUtf8();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(requestBody);

            String url = jsonNode.get("url").asText();
            assertFalse(url.contains("deployId"));
        }

        @Test
        public void shouldIncludeDeployIdInSyncRequestIfPresent() throws Exception {
            mockWebServer.enqueue(new MockResponse().setBody("Success"));
            mockWebServer.enqueue(new MockResponse().setBody("Success"));
            mockWebServer.enqueue(new MockResponse().setBody("Success"));

            mockMvc.perform(put("/api/inngest")
                            .header("Host", "localhost:8080")
                            .param("deployId", "1"))
                    .andExpect(status().isOk());

            RecordedRequest recordedRequest = mockWebServer.takeRequest();

            assertEquals("/fn/register", recordedRequest.getRequestUrl().encodedPath());
            assertEquals("1", recordedRequest.getRequestUrl().queryParameter("deployId"));
            assertThatPayloadDoesNotContainDeployId(recordedRequest);

            mockMvc.perform(put("/api/inngest")
                            .header("Host", "localhost:8080"))
                    .andExpect(status().isOk());

            recordedRequest = mockWebServer.takeRequest();

            assertEquals("/fn/register", recordedRequest.getRequestUrl().encodedPath());
            assertNull(recordedRequest.getRequestUrl().queryParameter("deployId"));
            assertThatPayloadDoesNotContainDeployId(recordedRequest);

            mockMvc.perform(put("/api/inngest")
                            .header("Host", "localhost:8080")
                            .param("deployId", "3"))
                    .andExpect(status().isOk());

            recordedRequest = mockWebServer.takeRequest();

            assertEquals("/fn/register", recordedRequest.getRequestUrl().encodedPath());
            assertEquals("3", recordedRequest.getRequestUrl().queryParameter("deployId"));
            assertThatPayloadDoesNotContainDeployId(recordedRequest);
        }
    }
}
