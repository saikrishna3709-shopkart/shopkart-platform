package com.shopkart.smoke;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PublicApiSmokeTest {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest
    @MethodSource("publicRoutes")
    void publicRouteReturnsJson(String path) throws Exception {
        String baseUrl = requiredProperty("shopkart.baseUrl").replaceAll("/+$", "");
        String deployedService = requiredProperty("shopkart.service");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode(),
                () -> deployedService + " deployment check failed at " + path + ": " + response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"),
                () -> path + " did not return application/json");
        objectMapper.readTree(response.body());
    }

    static Stream<String> publicRoutes() {
        return Stream.of("/api/products", "/api/orders?customer_id=smoke-check");
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is missing: " + name);
        }
        return value;
    }
}