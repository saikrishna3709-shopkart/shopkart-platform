package com.shopkart.inventory.reservation;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByOrderIdAndSku(String orderId, String sku);

    List<Reservation> findByOrderIdOrderById(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT reservation FROM Reservation reservation
            WHERE reservation.orderId = :orderId AND reservation.status = :status
            ORDER BY reservation.id
            """)
    List<Reservation> findForUpdate(
            @Param("orderId") String orderId,
            @Param("status") ReservationStatus status);
}