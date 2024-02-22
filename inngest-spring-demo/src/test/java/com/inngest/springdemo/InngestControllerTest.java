package com.inngest.springdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InngestController.class)
public class InngestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void shouldReturnSyncPayload() throws Exception {
        mockMvc.perform(get("/api/inngest"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.appName").value("my-app"))
            .andExpect(jsonPath("$.sdk").value("kotlin"));
    }
}
