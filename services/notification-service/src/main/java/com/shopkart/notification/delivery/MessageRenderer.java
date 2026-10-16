package com.shopkart.notification.delivery;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class MessageRenderer {

    private static final Map<OrderEvent, String> TEMPLATES = Map.of(
            OrderEvent.ORDER_CONFIRMED,
            "ShopKart: order %s confirmed%s. We'll let you know when it ships.",
            OrderEvent.ORDER_SHIPPED,
            "ShopKart: your order %s has shipped and is on the way%s.",
            OrderEvent.OUT_FOR_DELIVERY,
            "ShopKart: order %s is out for delivery today. Please keep your phone handy.",
            OrderEvent.DELIVERED,
            "ShopKart: order %s has been delivered. Thanks for shopping with us.",
            OrderEvent.ORDER_FAILED,
            "ShopKart: we couldn't complete order %s. Any amount held will be released.");

    public String render(OrderEvent event, String orderId, BigDecimal amount, String trackingId) {
        String shortId = orderId.substring(0, Math.min(8, orderId.length()));
        return switch (event) {
            case ORDER_CONFIRMED -> TEMPLATES.get(event).formatted(
                    shortId, amount == null ? "" : " for Rs " + amount.toPlainString());
            case ORDER_SHIPPED -> TEMPLATES.get(event).formatted(
                    shortId, trackingId == null || trackingId.isBlank() ? "" : " (tracking " + trackingId + ")");
            default -> TEMPLATES.get(event).formatted(shortId);
        };
    }
}