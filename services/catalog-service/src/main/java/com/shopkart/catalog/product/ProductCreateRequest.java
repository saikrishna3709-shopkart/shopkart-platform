package com.shopkart.catalog.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotBlank @Size(min = 3, max = 32) String sku,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 512) String description,
        @NotBlank @Size(max = 48) String category,
        @Size(max = 64) String brand,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price) {

    String normalizedDescription() {
        return description == null ? "" : description;
    }

    String normalizedBrand() {
        return brand == null ? "" : brand;
    }
}