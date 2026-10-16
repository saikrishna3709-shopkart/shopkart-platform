package com.shopkart.notification.service;

import com.shopkart.notification.delivery.DeliveryException;
import com.shopkart.notification.delivery.DeliveryProvider;
import com.shopkart.notification.delivery.DeliveryStatus;
import com.shopkart.notification.notification.Notification;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationDelivery {

    private final DeliveryProvider provider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final int maxAttempts;

    public NotificationDelivery(
            DeliveryProvider provider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${notification.max-delivery-attempts}") int maxAttempts) {
        this.provider = provider;
        this.meterRegistryProvider = meterRegistryProvider;
        this.maxAttempts = maxAttempts;
    }

    public void attempt(Notification notification) {
        try {
            provider.send(notification.getChannel(), notification.getRecipient(), notification.getBody());
            notification.sent();
        } catch (DeliveryException exception) {
            notification.failed(exception.getMessage(), maxAttempts);
            if (notification.getStatus() == DeliveryStatus.ABANDONED) {
                increment("shopkart.notifications.abandoned", new String[0]);
            }
        }
        increment(
                "shopkart.notifications",
                "event", notification.getEvent().name(),
                "channel", notification.getChannel().name(),
                "status", notification.getStatus().name());
    }

    private void increment(String name, String... tags) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(name, tags).increment();
        }
    }
}