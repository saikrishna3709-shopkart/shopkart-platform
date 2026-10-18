package com.shopkart.storefront;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "info.app.revision=test-revision")
@AutoConfigureMockMvc
class StorefrontServiceApplicationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void storefrontAndPlatformEndpointsAreAvailable() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
            .andExpect(forwardedUrl("index.html"));
        mockMvc.perform(get("/index.html"))
            .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ShopKart")));
        mockMvc.perform(get("/healthz")).andExpect(status().isOk());
        mockMvc.perform(get("/readyz")).andExpect(status().isOk());
        mockMvc.perform(get("/metrics")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.app.service").value("storefront-service"))
            .andExpect(jsonPath("$.app.revision").value("test-revision"));
        mockMvc.perform(get("/api/deployments/storefront-service"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.app.revision").value("test-revision"));
    }
}