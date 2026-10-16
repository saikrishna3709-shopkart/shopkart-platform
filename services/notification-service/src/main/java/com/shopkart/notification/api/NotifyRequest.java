package com.shopkart.notification.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.notification.delivery.OrderEvent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotifyRequest(
        @JsonProperty("order_id") @NotBlank @Size(max = 36) String orderId,
        @JsonProperty("customer_id") @NotBlank @Size(max = 36) String customerId,
        @NotNull OrderEvent event,
        @Size(max = 120) String email,
        @Size(max = 20) String phone,
        BigDecimal amount,
        @JsonProperty("tracking_id") @Size(max = 32) String trackingId) {
}