package com.shopkart.catalog.product;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;

public record ProductResponse(
        String sku,
        String name,
        String description,
        String category,
        String brand,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
        boolean active) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getCategory(),
                product.getBrand(),
                product.getPrice(),
                product.isActive());
    }
}