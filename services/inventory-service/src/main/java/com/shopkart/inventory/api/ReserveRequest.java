package com.shopkart.inventory.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReserveRequest(
        @JsonProperty("order_id") @NotBlank @Size(max = 36) String orderId,
        @NotBlank @Size(min = 3, max = 32) String sku,
        @Min(1) int quantity) {
}