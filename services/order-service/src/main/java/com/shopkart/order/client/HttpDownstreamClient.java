package com.shopkart.order.client;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.order.config.ServiceProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpDownstreamClient implements DownstreamClient {

    private final RestClient criticalClient;
    private final RestClient notificationClient;
    private final ServiceProperties properties;

    public HttpDownstreamClient(
            @Qualifier("criticalRestClient") RestClient criticalClient,
            @Qualifier("notificationRestClient") RestClient notificationClient,
            ServiceProperties properties) {
        this.criticalClient = criticalClient;
        this.notificationClient = notificationClient;
        this.properties = properties;
    }

    @Override
    public Optional<ProductInfo> getProduct(String sku) {
        try {
            ProductPayload body = criticalClient.get()
                    .uri(url(properties.catalogUrl(), "/products/{sku}"), sku)
                    .retrieve().body(ProductPayload.class);
            return body == null ? Optional.empty() : Optional.of(new ProductInfo(body.sku(), body.price(), body.active()));
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw unavailable("catalog", exception);
        } catch (RestClientException exception) {
            throw unavailable("catalog", exception);
        }
    }

    @Override
    public ReserveOutcome reserveStock(String orderId, String sku, int quantity) {
        Map<String, Object> request = Map.of("order_id", orderId, "sku", sku, "quantity", quantity);
        try {
            ReservePayload body = criticalClient.post()
                    .uri(url(properties.inventoryUrl(), "/inventory/reserve"))
                    .body(request).retrieve().body(ReservePayload.class);
            if (body == null) {
                throw new RestClientException("empty inventory response");
            }
            return new ReserveOutcome(body.reserved(), body.reason());
        } catch (RestClientException exception) {
            throw unavailable("inventory", exception);
        }
    }

    @Override
    public void releaseStock(String orderId) {
        postInventoryAction("release", orderId);
    }

    @Override
    public void commitStock(String orderId) {
        postInventoryAction("commit", orderId);
    }

    @Override
    public PaymentOutcome charge(String orderId, BigDecimal amount, String cardToken) {
        Map<String, Object> request = Map.of(
                "order_id", orderId,
                "amount", amount.toPlainString(),
                "card_token", cardToken);
        try {
            PaymentPayload body = criticalClient.post()
                    .uri(url(properties.paymentUrl(), "/payments"))
                    .body(request).retrieve().body(PaymentPayload.class);
            if (body == null) {
                throw new RestClientException("empty payment response");
            }
            return new PaymentOutcome("APPROVED".equals(body.status()), body.reason());
        } catch (RestClientException exception) {
            throw unavailable("payment", exception);
        }
    }

    @Override
    public boolean notify(
            String orderId, String customerId, String event, String email, String phone,
            BigDecimal amount, String trackingId) {
        if (isBlank(email) && isBlank(phone)) {
            return false;
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("order_id", orderId);
        request.put("customer_id", customerId);
        request.put("event", event);
        request.put("email", email);
        request.put("phone", phone);
        request.put("amount", amount == null ? null : amount.toPlainString());
        request.put("tracking_id", trackingId);
        try {
            notificationClient.post()
                    .uri(url(properties.notificationUrl(), "/notifications"))
                    .body(request).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientException exception) {
            return false;
        }
    }

    private void postInventoryAction(String action, String orderId) {
        try {
            criticalClient.post()
                    .uri(url(properties.inventoryUrl(), "/inventory/" + action))
                    .body(Map.of("order_id", orderId)).retrieve().toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable("inventory " + action, exception);
        }
    }

    private String url(String base, String path) {
        return base.replaceAll("/+$", "") + path;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private DownstreamUnavailableException unavailable(String service, RestClientException cause) {
        return new DownstreamUnavailableException(service + ": " + cause.getMessage(), cause);
    }

    private record ProductPayload(String sku, BigDecimal price, boolean active) {
    }

    private record ReservePayload(boolean reserved, String reason) {
    }

    private record PaymentPayload(String status, String reason) {
    }
}