package com.shopkart.order.service;

import java.util.List;

import com.shopkart.order.api.OrderCreateRequest;
import com.shopkart.order.api.OrderResponse;
import com.shopkart.order.api.StatusUpdateRequest;
import com.shopkart.order.domain.Order;
import com.shopkart.order.domain.OrderRepository;
import com.shopkart.order.domain.OrderStatus;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderWriter {

    private final OrderRepository repository;

    public OrderWriter(OrderRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order create(
            String orderId, String idempotencyKey, OrderCreateRequest request,
            List<PricedItem> priced, OrderStatus status, String failureReason) {
        java.math.BigDecimal total = priced.stream()
                .map(item -> item.unitPrice().multiply(java.math.BigDecimal.valueOf(item.quantity())))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        Order order = new Order(
                orderId, idempotencyKey, request.customerId(), request.email(), request.phone(),
                total, status, failureReason);
        priced.forEach(item -> order.addItem(item.sku(), item.quantity(), item.unitPrice()));
        return repository.saveAndFlush(order);
    }

    @Transactional
    public TransitionResult advance(String orderId, StatusUpdateRequest request) {
        Order order = repository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        order.getItems().size();
        if (order.getStatus() == request.status()) {
            return new TransitionResult(order, false);
        }
        if (order.getStatus().next() != request.status()) {
            throw new InvalidTransitionException(
                    "cannot move from " + order.getStatus() + " to " + request.status());
        }
        order.advance(request.status(), request.trackingId());
        repository.flush();
        return new TransitionResult(order, true);
    }

    public record PricedItem(String sku, int quantity, java.math.BigDecimal unitPrice) {
    }

    public record TransitionResult(Order order, boolean changed) {
    }
}