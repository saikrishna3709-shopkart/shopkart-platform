package com.shopkart.order.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.shopkart.order.api.OrderCreateRequest;
import com.shopkart.order.api.OrderItemRequest;
import com.shopkart.order.api.OrderResponse;
import com.shopkart.order.api.StatusUpdateRequest;
import com.shopkart.order.client.DownstreamClient;
import com.shopkart.order.client.DownstreamUnavailableException;
import com.shopkart.order.client.PaymentOutcome;
import com.shopkart.order.client.ProductInfo;
import com.shopkart.order.client.ReserveOutcome;
import com.shopkart.order.domain.Order;
import com.shopkart.order.domain.OrderRepository;
import com.shopkart.order.domain.OrderStatus;
import com.shopkart.order.service.OrderWriter.PricedItem;
import com.shopkart.order.service.OrderWriter.TransitionResult;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Map<OrderStatus, String> STATUS_EVENTS = Map.of(
            OrderStatus.CONFIRMED, "ORDER_CONFIRMED",
            OrderStatus.SHIPPED, "ORDER_SHIPPED",
            OrderStatus.OUT_FOR_DELIVERY, "OUT_FOR_DELIVERY",
            OrderStatus.DELIVERED, "DELIVERED",
            OrderStatus.FAILED, "ORDER_FAILED");

    private final OrderRepository repository;
    private final OrderWriter writer;
    private final DownstreamClient downstream;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final int maxItems;

    public OrderService(
            OrderRepository repository,
            OrderWriter writer,
            DownstreamClient downstream,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Value("${order.max-items}") int maxItems) {
        this.repository = repository;
        this.writer = writer;
        this.downstream = downstream;
        this.meterRegistryProvider = meterRegistryProvider;
        this.maxItems = maxItems;
    }

    public OrderResponse place(OrderCreateRequest request, String idempotencyKey) {
        Optional<Order> replay = repository.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            increment("shopkart.orders", "outcome", "IDEMPOTENT_REPLAY");
            return OrderResponse.from(replay.get());
        }
        if (request.items().size() > maxItems) {
            throw new TooManyItemsException();
        }

        String orderId = UUID.nameUUIDFromBytes(
            ("shopkart-order:" + idempotencyKey).getBytes(StandardCharsets.UTF_8)).toString();
        List<PricedItem> priced = price(request.items(), orderId, idempotencyKey, request);
        if (priced == null) {
            return existingByKey(idempotencyKey);
        }

        try {
            for (PricedItem item : priced) {
                ReserveOutcome outcome = downstream.reserveStock(orderId, item.sku(), item.quantity());
                if (!outcome.reserved()) {
                    compensate(orderId);
                    increment("shopkart.orders", "outcome", outcome.reason());
                    return persist(orderId, idempotencyKey, request, priced, OrderStatus.FAILED, outcome.reason());
                }
            }
        } catch (DownstreamUnavailableException exception) {
            compensate(orderId);
            increment("shopkart.orders", "outcome", "INVENTORY_DOWN");
            throw new DependencyUnavailableException("inventory unavailable", exception);
        }

        BigDecimal total = total(priced);
        PaymentOutcome payment;
        try {
            payment = downstream.charge(orderId, total, request.cardToken());
        } catch (DownstreamUnavailableException exception) {
            compensate(orderId);
            increment("shopkart.orders", "outcome", "PAYMENT_DOWN");
            throw new DependencyUnavailableException("payment unavailable", exception);
        }

        if (!payment.approved()) {
            compensate(orderId);
            increment("shopkart.orders", "outcome", payment.reason());
            return persist(orderId, idempotencyKey, request, priced, OrderStatus.FAILED, payment.reason());
        }

        try {
            downstream.commitStock(orderId);
        } catch (DownstreamUnavailableException ignored) {
            // Payment is complete. Reconciliation repairs a failed inventory commit.
        }
        increment("shopkart.orders", "outcome", "CONFIRMED");
        return persist(orderId, idempotencyKey, request, priced, OrderStatus.CONFIRMED, "");
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String orderId) {
        return repository.findByOrderId(orderId)
                .map(OrderResponse::from)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> list(String customerId) {
        return repository.findByCustomerIdOrderById(customerId).stream().map(OrderResponse::from).toList();
    }

    public OrderResponse advance(String orderId, StatusUpdateRequest request) {
        TransitionResult result = writer.advance(orderId, request);
        if (result.changed()) {
            announce(result.order());
        }
        return OrderResponse.from(result.order());
    }

    private List<PricedItem> price(
            List<OrderItemRequest> items, String orderId, String idempotencyKey, OrderCreateRequest request) {
        List<PricedItem> priced = new ArrayList<>();
        try {
            for (OrderItemRequest item : items) {
                ProductInfo product = downstream.getProduct(item.sku()).orElse(null);
                if (product == null || !product.active()) {
                    increment("shopkart.orders", "outcome", "PRODUCT_UNAVAILABLE");
                    persist(orderId, idempotencyKey, request, List.of(), OrderStatus.FAILED,
                            "UNAVAILABLE:" + item.sku());
                    return null;
                }
                priced.add(new PricedItem(item.sku(), item.quantity(), product.price()));
            }
            return priced;
        } catch (DownstreamUnavailableException exception) {
            increment("shopkart.orders", "outcome", "CATALOG_DOWN");
            throw new DependencyUnavailableException("catalog unavailable", exception);
        }
    }

    private OrderResponse persist(
            String orderId, String idempotencyKey, OrderCreateRequest request, List<PricedItem> priced,
            OrderStatus status, String reason) {
        Order order;
        try {
            order = writer.create(orderId, idempotencyKey, request, priced, status, reason);
        } catch (DataIntegrityViolationException exception) {
            order = repository.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> exception);
        }
        announce(order);
        return OrderResponse.from(order);
    }

    private OrderResponse existingByKey(String idempotencyKey) {
        return repository.findByIdempotencyKey(idempotencyKey)
                .map(OrderResponse::from)
                .orElseThrow(() -> new IllegalStateException("failed order was not persisted"));
    }

    private void announce(Order order) {
        if (order.getEmail().isBlank() && order.getPhone().isBlank()) {
            return;
        }
        String event = STATUS_EVENTS.get(order.getStatus());
        boolean delivered = downstream.notify(
                order.getOrderId(), order.getCustomerId(), event,
                blankToNull(order.getEmail()), blankToNull(order.getPhone()),
                order.getStatus() == OrderStatus.CONFIRMED ? order.getTotal() : null,
                blankToNull(order.getTrackingId()));
        if (!delivered) {
            increment("shopkart.notify.failures", "event", event);
        }
    }

    private void compensate(String orderId) {
        try {
            downstream.releaseStock(orderId);
            increment("shopkart.compensations");
        } catch (DownstreamUnavailableException ignored) {
            // Inventory's expiry/reconciliation process repairs failed compensation.
        }
    }

    private BigDecimal total(List<PricedItem> priced) {
        return priced.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void increment(String name, String... tags) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter(name, tags).increment();
        }
    }
}