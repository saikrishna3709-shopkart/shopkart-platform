package com.shopkart.notification.notification;

import java.util.List;

import com.shopkart.notification.delivery.DeliveryStatus;
import com.shopkart.notification.delivery.OrderEvent;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByOrderIdAndEventOrderById(String orderId, OrderEvent event);

    @Query("""
            SELECT notification FROM Notification notification
            WHERE (:orderId IS NULL OR notification.orderId = :orderId)
              AND (:customerId IS NULL OR notification.customerId = :customerId)
            ORDER BY notification.id
            """)
    List<Notification> findNotifications(
            @Param("orderId") String orderId,
            @Param("customerId") String customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT notification FROM Notification notification
            WHERE notification.status = :status
            ORDER BY notification.id
            """)
    List<Notification> findForRetry(@Param("status") DeliveryStatus status);
}