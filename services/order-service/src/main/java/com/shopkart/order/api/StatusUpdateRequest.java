package com.shopkart.order.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.order.domain.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotNull OrderStatus status,
        @JsonProperty("tracking_id") @Size(max = 32) String trackingId) {
}