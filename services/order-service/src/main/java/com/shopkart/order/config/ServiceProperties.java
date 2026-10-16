package com.shopkart.order.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "services")
public record ServiceProperties(
        String catalogUrl,
        String inventoryUrl,
        String paymentUrl,
        String notificationUrl,
        Duration downstreamTimeout,
        Duration notificationTimeout) {
}