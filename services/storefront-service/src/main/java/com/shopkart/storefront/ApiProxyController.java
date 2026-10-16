package com.shopkart.storefront;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@RestController
public class ApiProxyController {

    private final RestClient restClient;
    private final StorefrontProperties properties;

    public ApiProxyController(RestClient restClient, StorefrontProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @GetMapping("/api/products")
    public ResponseEntity<byte[]> products(HttpServletRequest request) {
        String query = request.getQueryString();
        String target = base(properties.catalogUrl()) + "/products" + (query == null ? "" : "?" + query);
        try {
            return exchange(restClient.get().uri(target));
        } catch (RestClientException exception) {
            return unavailable("catalog unavailable");
        }
    }

    @PostMapping(value = "/api/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> orders(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody byte[] body) {
        try {
            return exchange(restClient.post()
                    .uri(base(properties.orderUrl()) + "/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", idempotencyKey)
                    .body(body));
        } catch (RestClientException exception) {
            return unavailable("checkout unavailable");
        }
    }

    private ResponseEntity<byte[]> exchange(RestClient.RequestHeadersSpec<?> request) {
        return request.exchange((clientRequest, clientResponse) -> {
            try {
                byte[] body = clientResponse.getBody().readAllBytes();
                HttpHeaders headers = new HttpHeaders();
                MediaType contentType = clientResponse.getHeaders().getContentType();
                if (contentType != null) {
                    headers.setContentType(contentType);
                }
                return ResponseEntity.status(clientResponse.getStatusCode()).headers(headers).body(body);
            } catch (IOException exception) {
                throw new RestClientException("failed to read downstream response", exception);
            }
        });
    }

    private ResponseEntity<byte[]> unavailable(String detail) {
        String body = "{\"detail\":\"" + detail + "\"}";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private String base(String value) {
        return value.replaceAll("/+$", "");
    }
}