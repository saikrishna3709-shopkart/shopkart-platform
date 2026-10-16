package com.shopkart.order.api;

import java.util.List;

import com.shopkart.order.service.OrderService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse place(
            @Valid @RequestBody OrderCreateRequest request,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 64) String idempotencyKey) {
        return service.place(request, idempotencyKey);
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse get(@PathVariable String orderId) {
        return service.get(orderId);
    }

    @PatchMapping("/orders/{orderId}/status")
    public OrderResponse advance(
            @PathVariable String orderId, @Valid @RequestBody StatusUpdateRequest request) {
        return service.advance(orderId, request);
    }

    @GetMapping("/orders")
    public List<OrderResponse> list(@RequestParam(name = "customer_id") String customerId) {
        return service.list(customerId);
    }
}