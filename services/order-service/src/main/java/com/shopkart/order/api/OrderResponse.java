package com.shopkart.order.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.order.domain.Order;
import com.shopkart.order.domain.OrderStatus;

public record OrderResponse(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("customer_id") String customerId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
        OrderStatus status,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("tracking_id") String trackingId,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        List<OrderItemResponse> items) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getOrderId(), order.getCustomerId(), order.getTotal(), order.getStatus(),
                order.getFailureReason(), order.getTrackingId(), order.getCreatedAt(),
                order.getItems().stream().map(OrderItemResponse::from).toList());
    }
}