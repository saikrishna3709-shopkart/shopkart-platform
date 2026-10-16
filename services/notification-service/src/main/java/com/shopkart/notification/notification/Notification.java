package com.shopkart.notification.notification;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.shopkart.notification.delivery.Channel;
import com.shopkart.notification.delivery.DeliveryStatus;
import com.shopkart.notification.delivery.OrderEvent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "notifications",
        indexes = {
                @Index(name = "idx_notifications_notification_id", columnList = "notification_id", unique = true),
                @Index(name = "idx_notifications_order_id", columnList = "order_id"),
                @Index(name = "idx_notifications_customer_id", columnList = "customer_id"),
                @Index(name = "idx_notifications_status", columnList = "status")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uq_order_event_channel", columnNames = {"order_id", "event", "channel"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notification_id", nullable = false, unique = true, length = 36)
    private String notificationId;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderEvent event;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Channel channel;

    @Column(nullable = false, length = 120)
    private String recipient;

    @Column(nullable = false, length = 320)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeliveryStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", nullable = false, length = 160)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Notification() {
    }

    public Notification(
            String notificationId, String orderId, String customerId, OrderEvent event,
            Channel channel, String recipient, String body) {
        this.notificationId = notificationId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.event = event;
        this.channel = channel;
        this.recipient = recipient;
        this.body = body;
        this.status = DeliveryStatus.FAILED;
        this.attempts = 0;
        this.lastError = "";
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getNotificationId() { return notificationId; }
    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public OrderEvent getEvent() { return event; }
    public Channel getChannel() { return channel; }
    public String getRecipient() { return recipient; }
    public String getBody() { return body; }
    public DeliveryStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public String getLastError() { return lastError; }

    public void sent() {
        attempts++;
        status = DeliveryStatus.SENT;
        lastError = "";
    }

    public void failed(String error, int maxAttempts) {
        attempts++;
        status = attempts >= maxAttempts ? DeliveryStatus.ABANDONED : DeliveryStatus.FAILED;
        lastError = error.substring(0, Math.min(160, error.length()));
    }
}