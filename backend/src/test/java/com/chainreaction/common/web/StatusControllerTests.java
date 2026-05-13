package com.chainreaction.common.web;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import com.chainreaction.common.security.SecurityConfig;

@WebMvcTest(StatusController.class)
@Import(SecurityConfig.class)
class StatusControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void statusEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service", equalTo("chain-stories-backend")))
                .andExpect(jsonPath("$.status", equalTo("ok")));
    }
}
