package com.shopkart.storefront;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storefront")
public record StorefrontProperties(
        String catalogUrl,
        String orderUrl,
        String inventoryUrl,
        String paymentUrl,
        String notificationUrl,
        Duration downstreamTimeout) {
}