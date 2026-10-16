package com.shopkart.order.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_order_id", columnList = "order_id", unique = true),
        @Index(name = "idx_orders_idempotency_key", columnList = "idempotency_key", unique = true),
        @Index(name = "idx_orders_customer_id", columnList = "customer_id")
})
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 36)
    private String orderId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "tracking_id", nullable = false, length = 32)
    private String trackingId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OrderStatus status;

    @Column(name = "failure_reason", nullable = false, length = 64)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public Order(
            String orderId, String idempotencyKey, String customerId, String email, String phone,
            BigDecimal total, OrderStatus status, String failureReason) {
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.customerId = customerId;
        this.email = email == null ? "" : email;
        this.phone = phone == null ? "" : phone;
        this.trackingId = "";
        this.total = total;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void addItem(String sku, int quantity, BigDecimal unitPrice) {
        items.add(new OrderItem(this, sku, quantity, unitPrice));
    }

    public void advance(OrderStatus target, String trackingId) {
        status = target;
        if (trackingId != null && !trackingId.isBlank()) {
            this.trackingId = trackingId;
        }
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getTrackingId() { return trackingId; }
    public BigDecimal getTotal() { return total; }
    public OrderStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public List<OrderItem> getItems() { return items; }
}