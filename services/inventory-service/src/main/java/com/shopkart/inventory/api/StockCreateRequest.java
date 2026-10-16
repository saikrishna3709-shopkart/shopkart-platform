package com.shopkart.inventory.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockCreateRequest(
        @NotBlank @Size(min = 3, max = 32) String sku,
        @Min(0) int quantity) {
}