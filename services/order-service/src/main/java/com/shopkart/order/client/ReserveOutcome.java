package com.shopkart.order.client;

public record ReserveOutcome(boolean reserved, String reason) {
}