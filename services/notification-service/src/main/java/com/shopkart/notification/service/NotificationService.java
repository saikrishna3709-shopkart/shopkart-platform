package com.shopkart.notification.service;

import java.util.List;

import com.shopkart.notification.api.NotificationResponse;
import com.shopkart.notification.api.NotifyRequest;
import com.shopkart.notification.api.RetryResult;
import com.shopkart.notification.delivery.DeliveryStatus;
import com.shopkart.notification.notification.Notification;
import com.shopkart.notification.notification.NotificationRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationBatchCreator creator;
    private final NotificationDelivery delivery;

    public NotificationService(
            NotificationRepository repository,
            NotificationBatchCreator creator,
            NotificationDelivery delivery) {
        this.repository = repository;
        this.creator = creator;
        this.delivery = delivery;
    }

    public List<NotificationResponse> notify(NotifyRequest request) {
        if (isBlank(request.email()) && isBlank(request.phone())) {
            throw new NoRecipientException();
        }
        try {
            return creator.create(request).stream().map(NotificationResponse::from).toList();
        } catch (DataIntegrityViolationException exception) {
            List<Notification> replay = repository.findByOrderIdAndEventOrderById(request.orderId(), request.event());
            if (replay.isEmpty()) {
                throw exception;
            }
            return replay.stream().map(NotificationResponse::from).toList();
        }
    }

    @Transactional
    public RetryResult retryFailed() {
        List<Notification> pending = repository.findForRetry(DeliveryStatus.FAILED);
        int delivered = 0;
        int abandoned = 0;
        for (Notification notification : pending) {
            delivery.attempt(notification);
            if (notification.getStatus() == DeliveryStatus.SENT) {
                delivered++;
            } else if (notification.getStatus() == DeliveryStatus.ABANDONED) {
                abandoned++;
            }
        }
        return new RetryResult(pending.size(), delivered, abandoned);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(String orderId, String customerId) {
        return repository.findNotifications(normalize(orderId), normalize(customerId)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return isBlank(value) ? null : value;
    }
}