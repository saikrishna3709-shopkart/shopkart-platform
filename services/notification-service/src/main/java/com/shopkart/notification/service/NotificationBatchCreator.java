package com.shopkart.notification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.shopkart.notification.api.NotifyRequest;
import com.shopkart.notification.delivery.Channel;
import com.shopkart.notification.delivery.MessageRenderer;
import com.shopkart.notification.notification.Notification;
import com.shopkart.notification.notification.NotificationRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationBatchCreator {

    private final NotificationRepository repository;
    private final MessageRenderer renderer;
    private final NotificationDelivery delivery;

    public NotificationBatchCreator(
            NotificationRepository repository, MessageRenderer renderer, NotificationDelivery delivery) {
        this.repository = repository;
        this.renderer = renderer;
        this.delivery = delivery;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Notification> create(NotifyRequest request) {
        List<Notification> existing = repository.findByOrderIdAndEventOrderById(request.orderId(), request.event());
        if (!existing.isEmpty()) {
            return existing;
        }

        String body = renderer.render(request.event(), request.orderId(), request.amount(), request.trackingId());
        List<Notification> created = new ArrayList<>();
        add(created, request, Channel.EMAIL, request.email(), body);
        add(created, request, Channel.SMS, request.phone(), body);
        repository.saveAllAndFlush(created);
        created.forEach(delivery::attempt);
        return created;
    }

    private void add(
            List<Notification> created, NotifyRequest request, Channel channel, String recipient, String body) {
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        Notification notification = new Notification(
                UUID.randomUUID().toString(), request.orderId(), request.customerId(),
                request.event(), channel, recipient, body);
        created.add(notification);
    }
}