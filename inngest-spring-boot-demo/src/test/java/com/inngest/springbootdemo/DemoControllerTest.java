package com.inngest.springbootdemo;

import com.inngest.InngestHeaderKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(DemoTestConfiguration.class)
@WebMvcTest(DemoController.class)
public class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnSyncPayload() throws Exception {
        mockMvc.perform(get("/api/inngest").header("Host", "localhost:8080"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(header().string(InngestHeaderKey.Framework.getValue(), "springboot"))
            .andExpect(jsonPath("$.appName").value("spring_test_demo"))
            .andExpect(jsonPath("$.url").value("http://localhost:8080/api/inngest"))
            .andExpect(jsonPath("$.sdk").value("inngest-kt"));
    }
}
