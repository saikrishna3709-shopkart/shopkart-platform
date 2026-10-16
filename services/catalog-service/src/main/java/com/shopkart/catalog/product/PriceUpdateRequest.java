package com.shopkart.catalog.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PriceUpdateRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal price) {
}