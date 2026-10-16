package com.shopkart.notification.api;

import java.util.Arrays;
import java.util.List;

import com.shopkart.notification.delivery.OrderEvent;
import com.shopkart.notification.service.NotificationService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/notifications")
    @ResponseStatus(HttpStatus.CREATED)
    public List<NotificationResponse> notify(@Valid @RequestBody NotifyRequest request) {
        return service.notify(request);
    }

    @PostMapping("/notifications/retry")
    public RetryResult retry() {
        return service.retryFailed();
    }

    @GetMapping("/notifications")
    public List<NotificationResponse> list(
            @RequestParam(name = "order_id", required = false) String orderId,
            @RequestParam(name = "customer_id", required = false) String customerId) {
        return service.list(orderId, customerId);
    }

    @GetMapping("/events")
    public List<String> events() {
        return Arrays.stream(OrderEvent.values()).map(Enum::name).toList();
    }
}