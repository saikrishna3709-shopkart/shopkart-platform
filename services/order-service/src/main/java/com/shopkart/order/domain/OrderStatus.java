package com.shopkart.order.domain;

public enum OrderStatus {
    CONFIRMED,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED;

    public OrderStatus next() {
        return switch (this) {
            case CONFIRMED -> SHIPPED;
            case SHIPPED -> OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> DELIVERED;
            default -> null;
        };
    }
}