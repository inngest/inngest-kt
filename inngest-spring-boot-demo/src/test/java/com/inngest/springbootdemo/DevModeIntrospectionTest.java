package com.inngest.springbootdemo;

import com.inngest.InngestHeaderKey;
import com.inngest.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(DemoTestConfiguration.class)
@WebMvcTest(DemoController.class)
public class DevModeIntrospectionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @EnabledIfSystemProperty(named = "test-group", matches = "unit-test")
    public void shouldReturnInsecureIntrospectPayload() throws Exception {
        mockMvc.perform(get("/api/inngest").header("Host", "localhost:8080"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(header().string(InngestHeaderKey.Framework.getValue(), "springboot"))
            .andExpect(jsonPath("$.authentication_succeeded").isEmpty())
            .andExpect(jsonPath("$.function_count").isNumber())
            .andExpect(jsonPath("$.has_event_key").value(false))
            .andExpect(jsonPath("$.has_signing_key").value(false))
            .andExpect(jsonPath("$.mode").value("dev"))
            .andExpect(jsonPath("$.schema_version").value("2024-05-24"));
    }
}
