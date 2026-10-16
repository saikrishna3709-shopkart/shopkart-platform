package com.shopkart.order.api;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.order.domain.OrderItem;

public record OrderItemResponse(
        String sku,
        int quantity,
        @JsonProperty("unit_price") @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice) {

    static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getSku(), item.getQuantity(), item.getUnitPrice());
    }
}