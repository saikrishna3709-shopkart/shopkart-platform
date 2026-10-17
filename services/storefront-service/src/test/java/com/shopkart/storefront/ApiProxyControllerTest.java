package com.shopkart.storefront;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

class ApiProxyControllerTest {

    private MockRestServiceServer server;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        StorefrontProperties properties = new StorefrontProperties(
                "http://catalog", "http://orders", "http://inventory", "http://payment",
                "http://notification", Duration.ofSeconds(3));
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ApiProxyController(builder.build(), properties, "test-revision")).build();
    }

    @ParameterizedTest
    @CsvSource({
        "catalog-service,http://catalog",
        "order-service,http://orders",
        "inventory-service,http://inventory",
        "payment-service,http://payment",
        "notification-service,http://notification"
    })
    void proxiesOnlyKnownServiceRevisions(String service, String upstream) throws Exception {
        String response = "{\"app\":{\"service\":\"" + service + "\",\"revision\":\"abc123def456\"}}";
        server.expect(requestTo(upstream + "/actuator/info"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/deployments/" + service))
                .andExpect(status().isOk())
                .andExpect(content().json(response));
        server.verify();
    }

    @Test
    void reportsTheStorefrontRevisionWithoutADownstreamCall() throws Exception {
        mockMvc.perform(get("/api/deployments/storefront-service"))
                .andExpect(status().isOk())
                .andExpect(content().json(
                        "{\"app\":{\"service\":\"storefront-service\",\"revision\":\"test-revision\"}}"));
        server.verify();
    }

    @Test
    void rejectsUnknownDeploymentTargets() throws Exception {
        mockMvc.perform(get("/api/deployments/unknown-service"))
                .andExpect(status().isNotFound());
        server.verify();
    }

    @Test
    void reportsUnavailableDeploymentInformation() throws Exception {
        server.expect(requestTo("http://inventory/actuator/info"))
                .andRespond(withException(new IOException("connection refused")));

        mockMvc.perform(get("/api/deployments/inventory-service"))
                .andExpect(status().isBadGateway());
        server.verify();
    }

    @Test
    void proxiesTheProductCatalog() throws Exception {
        server.expect(requestTo("http://catalog/products?category=footwear"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"sku\":\"SHOE-001\"}]", MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/api/products").queryParam("category", "footwear"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[{\"sku\":\"SHOE-001\"}]"));
        server.verify();
    }

        @Test
        void proxiesOrderQueries() throws Exception {
                server.expect(requestTo("http://orders/orders?customer_id=smoke-check"))
                                .andExpect(method(HttpMethod.GET))
                                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

                mockMvc.perform(get("/api/orders").queryParam("customer_id", "smoke-check"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(content().json("[]"));
                server.verify();
        }

    @Test
    void preservesTheCheckoutIdempotencyKeyAndBody() throws Exception {
        server.expect(requestTo("http://orders/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Idempotency-Key", "demo-key-123"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("{\"customer_id\":\"web-1\",\"items\":[]}"))
                .andRespond(withSuccess("{\"status\":\"CONFIRMED\"}", MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "demo-key-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customer_id\":\"web-1\",\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"CONFIRMED\"}"));
        server.verify();
    }

        @Test
        void returnsJsonWhenCatalogIsUnavailable() throws Exception {
                server.expect(requestTo("http://catalog/products"))
                                .andRespond(withException(new IOException("connection refused")));

                mockMvc.perform(get("/api/products"))
                                .andExpect(status().isBadGateway())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(content().json("{\"detail\":\"catalog unavailable\"}"));
                server.verify();
        }

        @Test
        void returnsJsonWhenCheckoutIsUnavailable() throws Exception {
                server.expect(requestTo("http://orders/orders"))
                                .andRespond(withException(new IOException("connection refused")));

                mockMvc.perform(post("/api/orders")
                                                .header("Idempotency-Key", "demo-key-123")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{}"))
                                .andExpect(status().isBadGateway())
                                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                                .andExpect(content().json("{\"detail\":\"checkout unavailable\"}"));
                server.verify();
        }
}