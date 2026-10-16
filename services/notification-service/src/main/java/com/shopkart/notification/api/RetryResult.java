package com.shopkart.notification.api;

public record RetryResult(int retried, int delivered, int abandoned) {
}