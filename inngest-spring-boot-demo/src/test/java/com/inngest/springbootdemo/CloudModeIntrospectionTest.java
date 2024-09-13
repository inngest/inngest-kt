package com.inngest.springbootdemo;

import com.inngest.*;
import com.inngest.signingkey.BearerTokenKt;
import com.inngest.signingkey.SignatureVerificationKt;
import com.inngest.springboot.InngestConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductionConfiguration extends InngestConfiguration {

    public static final String INNGEST_APP_ID = "spring_test_prod_demo";

    @Override
    protected HashMap<String, InngestFunction> functions() {
        return new HashMap<>();
    }

    @Override
    protected Inngest inngestClient() {
        return new Inngest(INNGEST_APP_ID);
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

@ExtendWith(SystemStubsExtension.class)
public class CloudModeIntrospectionTest {

    private static final String productionSigningKey = "signkey-prod-b2ed992186a5cb19f6668aade821f502c1d00970dfd0e35128d51bac4649916c";
    private static final String productionEventKey = "test";
    @SystemStub
    private static EnvironmentVariables environmentVariables;

    @BeforeAll
    static void beforeAll() {
        environmentVariables.set("INNGEST_DEV", "0");
        environmentVariables.set("INNGEST_SIGNING_KEY", productionSigningKey);
        environmentVariables.set("INNGEST_EVENT_KEY", productionEventKey);
    }

    // The nested class is useful for setting the environment variables before the configuration class (Beans) runs.
    // https://www.baeldung.com/java-system-stubs#environment-and-property-overrides-for-junit-5-spring-tests
    @Import(ProductionConfiguration.class)
    @WebMvcTest(DemoController.class)
    @Nested
    @EnabledIfSystemProperty(named = "test-group", matches = "unit-test")
    class InnerSpringTest {
        @Autowired
        private MockMvc mockMvc;

        @Test
        public void shouldReturnInsecureIntrospectionWhenSignatureIsMissing() throws Exception {
            mockMvc.perform(get("/api/inngest").header("Host", "localhost:8080"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(header().string(InngestHeaderKey.Framework.getValue(), "springboot"))
                    .andExpect(jsonPath("$.authentication_succeeded").value(false))
                    .andExpect(jsonPath("$.function_count").isNumber())
                    .andExpect(jsonPath("$.has_event_key").value(true))
                    .andExpect(jsonPath("$.has_signing_key").value(true))
                    .andExpect(jsonPath("$.mode").value("cloud"))
                    .andExpect(jsonPath("$.schema_version").value("2024-05-24"));
        }

        @Test
        public void shouldReturnInsecureIntrospectionWhenSignatureIsInvalid() throws Exception {
            mockMvc.perform(get("/api/inngest")
                            .header("Host", "localhost:8080")
                            .header(InngestHeaderKey.Signature.getValue(), "invalid-signature"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(header().string(InngestHeaderKey.Framework.getValue(), "springboot"))
                    .andExpect(jsonPath("$.authentication_succeeded").value(false))
                    .andExpect(jsonPath("$.function_count").isNumber())
                    .andExpect(jsonPath("$.has_event_key").value(true))
                    .andExpect(jsonPath("$.has_signing_key").value(true))
                    .andExpect(jsonPath("$.mode").value("cloud"))
                    .andExpect(jsonPath("$.schema_version").value("2024-05-24"));
        }

        @Test
        public void shouldReturnSecureIntrospectionWhenSignatureIsValid() throws Exception {
            long currentTimestamp = System.currentTimeMillis() / 1000;

            String signature = SignatureVerificationKt.signRequest("", currentTimestamp, productionSigningKey);
            String formattedSignature = String.format("s=%s&t=%d", signature, currentTimestamp);

            String expectedSigningKeyHash = BearerTokenKt.hashedSigningKey(productionSigningKey);

            mockMvc.perform(get("/api/inngest")
                            .header("Host", "localhost:8080")
                            .header(InngestHeaderKey.Signature.getValue(), formattedSignature))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/json"))
                    .andExpect(header().string(InngestHeaderKey.Framework.getValue(), "springboot"))
                    .andExpect(jsonPath("$.authentication_succeeded").value(true))
                    .andExpect(jsonPath("$.function_count").isNumber())
                    .andExpect(jsonPath("$.has_event_key").value(true))
                    .andExpect(jsonPath("$.has_signing_key").value(true))
                    .andExpect(jsonPath("$.mode").value("cloud"))
                    .andExpect(jsonPath("$.schema_version").value("2024-05-24"))
                    .andExpect(jsonPath("$.api_origin").value("https://api.inngest.com/"))
                    .andExpect(jsonPath("$.app_id").value(ProductionConfiguration.INNGEST_APP_ID))
                    .andExpect(jsonPath("$.env").value("prod"))
                    .andExpect(jsonPath("$.event_api_origin").value("https://inn.gs/"))
                    .andExpect(jsonPath("$.framework").value("springboot"))
                    .andExpect(jsonPath("$.sdk_language").value("java"))
                    .andExpect(jsonPath("$.event_key_hash").value("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"))
                    .andExpect(jsonPath("$.sdk_version").value(Version.Companion.getVersion()))
                    .andExpect(jsonPath("$.signing_key_hash").value(expectedSigningKeyHash));
        }
    }
}
