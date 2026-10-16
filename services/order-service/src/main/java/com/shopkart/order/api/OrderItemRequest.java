package com.shopkart.order.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderItemRequest(
        @NotBlank @Size(min = 3, max = 32) String sku,
        @Min(1) @Max(50) int quantity) {
}