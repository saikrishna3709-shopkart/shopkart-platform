package com.shopkart.notification.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.notification.delivery.Channel;
import com.shopkart.notification.delivery.DeliveryStatus;
import com.shopkart.notification.delivery.OrderEvent;
import com.shopkart.notification.notification.Notification;

public record NotificationResponse(
        @JsonProperty("notification_id") String notificationId,
        @JsonProperty("order_id") String orderId,
        OrderEvent event,
        Channel channel,
        String recipient,
        String body,
        DeliveryStatus status,
        int attempts,
        @JsonProperty("last_error") String lastError) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getNotificationId(), notification.getOrderId(), notification.getEvent(),
                notification.getChannel(), notification.getRecipient(), notification.getBody(),
                notification.getStatus(), notification.getAttempts(), notification.getLastError());
    }
}