package com.shopkart.order.client;

public record PaymentOutcome(boolean approved, String reason) {
}