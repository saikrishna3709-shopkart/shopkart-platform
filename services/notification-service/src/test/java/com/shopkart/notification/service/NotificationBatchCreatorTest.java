package com.shopkart.notification.service;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import com.shopkart.notification.api.NotifyRequest;
import com.shopkart.notification.delivery.MessageRenderer;
import com.shopkart.notification.delivery.OrderEvent;
import com.shopkart.notification.notification.NotificationRepository;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class NotificationBatchCreatorTest {

    @Test
    void claimsDeduplicationKeysBeforeExternalDelivery() {
        NotificationRepository repository = mock(NotificationRepository.class);
        MessageRenderer renderer = mock(MessageRenderer.class);
        NotificationDelivery delivery = mock(NotificationDelivery.class);
        NotifyRequest request = new NotifyRequest(
                "order-1", "cust-1", OrderEvent.ORDER_CONFIRMED,
                "buyer@example.com", null, null, null);

        when(repository.findByOrderIdAndEventOrderById("order-1", OrderEvent.ORDER_CONFIRMED))
                .thenReturn(List.of());
        when(renderer.render(OrderEvent.ORDER_CONFIRMED, "order-1", null, null))
                .thenReturn("confirmed");

        new NotificationBatchCreator(repository, renderer, delivery).create(request);

        InOrder ordering = inOrder(repository, delivery);
        ordering.verify(repository).saveAllAndFlush(anyList());
        ordering.verify(delivery).attempt(org.mockito.ArgumentMatchers.any());
    }
}