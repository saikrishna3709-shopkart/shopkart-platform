package com.shopkart.catalog.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void databaseIsAvailable() {
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);
    }

    @Test
    void healthDoesNotRequireDatabase() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"status\":\"ok\"}"));
    }

    @Test
    void readinessChecksDatabase() throws Exception {
        mockMvc.perform(get("/readyz"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ready\"}"));
    }

            @Test
            void readinessReturnsUnavailableWhenDatabaseFails() throws Exception {
            when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));

            mockMvc.perform(get("/readyz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("database unavailable"));
            }

    @Test
    void prometheusMetricsUseExistingPath() throws Exception {
        mockMvc.perform(get("/metrics"))
                .andExpect(status().isOk());
    }
}