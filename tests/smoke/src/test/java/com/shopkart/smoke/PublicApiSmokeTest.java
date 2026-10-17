package com.shopkart.smoke;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

class PublicApiSmokeTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> SERVICES = List.of(
        "catalog-service", "inventory-service", "order-service",
        "payment-service", "notification-service", "storefront-service");

    @BeforeAll
    static void waitForTheRequestedRevisionAndDependencies() {
    String service = requiredProperty("shopkart.service");
    String expectedRevision = requiredProperty("shopkart.expectedRevision");
    assertTrue(SERVICES.contains(service), "unknown ShopKart service: " + service);

    await().alias("expected revision and all six services")
        .pollDelay(Duration.ZERO)
        .pollInterval(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(Long.getLong("shopkart.deploymentTimeoutSeconds", 300L)))
        .ignoreExceptions()
        .untilAsserted(() -> {
            for (String dependency : SERVICES) {
            JsonNode info = getJson("/api/deployments/" + dependency).path("app");
            assertEquals(dependency, info.path("service").asText());
            String revision = info.path("revision").asText();
            assertTrue(!revision.isBlank() && !"PLACEHOLDER".equals(revision),
                dependency + " has no deployed revision");
            if (dependency.equals(service)) {
                assertEquals(expectedRevision, revision,
                    service + " is still serving a different revision");
            }
            }
        });
    }

    @ParameterizedTest
    @MethodSource("publicRoutes")
    void publicRouteReturnsJson(String path) throws Exception {
    assertTrue(getJson(path).isArray(), path + " must return a JSON array");
    }

    @Test
    void storefrontLoadsTheShoppingExperience() throws Exception {
        String baseUrl = requiredProperty("shopkart.baseUrl").replaceAll("/+$", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/"))
                .header("Accept", "text/html")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("text/html"));
        assertTrue(response.body().contains("id=\"product-grid\""));
        assertTrue(response.body().contains("/app.js"));
    }

    @Test
    void checkoutConfirmsAndAnIdempotentReplayCreatesNoDuplicateOrder() throws Exception {
        String customerId = "smoke-" + UUID.randomUUID().toString().substring(0, 24);
        String key = UUID.randomUUID().toString();
        Map<String, Object> payload = checkoutPayload(customerId, "tok_ok");

        JsonNode created = postOrder(payload, key);
        assertEquals("CONFIRMED", created.path("status").asText(), created.toString());
        assertTrue(new BigDecimal(created.path("total").asText()).signum() > 0,
            "the confirmed order total must be positive");
        JsonNode replay = postOrder(payload, key);
        assertEquals(created.path("order_id"), replay.path("order_id"));
        assertEquals(created.path("total"), replay.path("total"));
        assertEquals("CONFIRMED", replay.path("status").asText());

        JsonNode orders = getJson("/api/orders?customer_id=" + customerId);
        assertEquals(1, orders.size(), "the same idempotency key created duplicate orders");
        assertEquals(created.path("order_id"), orders.get(0).path("order_id"));
    }

    @Test
    void declinedPaymentIsReportedAsAFailedOrder() throws Exception {
        String customerId = "smoke-" + UUID.randomUUID().toString().substring(0, 24);
        JsonNode declined = postOrder(checkoutPayload(customerId, "tok_decline"), UUID.randomUUID().toString());
        assertEquals("FAILED", declined.path("status").asText(), declined.toString());
        assertEquals("CARD_DECLINED", declined.path("failure_reason").asText());
    }

    private Map<String, Object> checkoutPayload(String customerId, String token) throws Exception {
        JsonNode products = getJson("/api/products");
        assertTrue(products.isArray() && !products.isEmpty(), "the demo catalog must be seeded");
        String sku = System.getProperty("shopkart.sku", products.get(0).path("sku").asText());
        assertTrue(!sku.isBlank(), "the demo SKU is missing");
        return Map.of(
                "customer_id", customerId,
                "items", List.of(Map.of("sku", sku, "quantity", 1)),
                "card_token", token,
                "email", "smoke@example.test",
                "phone", "");
    }

    private JsonNode postOrder(Map<String, Object> payload, String key) throws Exception {
        HttpRequest request = request("/api/orders")
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", key)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, response.statusCode(), response.body());
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = CLIENT.send(
                request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), path + ": " + response.body());
        String contentType = response.headers().firstValue("Content-Type").orElse("")
            .split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        assertTrue("application/json".equals(contentType)
                || (contentType.startsWith("application/") && contentType.endsWith("+json")),
            path + " did not return a JSON content type: " + contentType);
        return OBJECT_MAPPER.readTree(response.body());
    }

    private static HttpRequest.Builder request(String path) {
        String baseUrl = requiredProperty("shopkart.baseUrl").replaceAll("/+$", "");
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10));
    }

    static Stream<String> publicRoutes() {
        return Stream.of("/api/products", "/api/orders?customer_id=smoke-check");
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is missing: " + name);
        }
        return value;
    }
}