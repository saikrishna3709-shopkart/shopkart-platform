package com.shopkart.inventory.service;

import com.shopkart.inventory.api.ReserveRequest;
import com.shopkart.inventory.api.ReserveResult;
import com.shopkart.inventory.reservation.Reservation;
import com.shopkart.inventory.reservation.ReservationRepository;
import com.shopkart.inventory.stock.StockRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReservationAttempt {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;

    public ReservationAttempt(StockRepository stockRepository, ReservationRepository reservationRepository) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReserveResult execute(ReserveRequest request) {
        Reservation existing = reservationRepository.findByOrderIdAndSku(request.orderId(), request.sku())
                .orElse(null);
        if (existing != null) {
            return replay(existing);
        }

        int updated = stockRepository.reserve(request.sku(), request.quantity());
        if (updated == 0) {
            Reservation completedByConcurrentRequest = reservationRepository
                    .findByOrderIdAndSku(request.orderId(), request.sku())
                    .orElse(null);
            if (completedByConcurrentRequest != null) {
                return replay(completedByConcurrentRequest);
            }
            String reason = stockRepository.existsBySku(request.sku())
                    ? "INSUFFICIENT_STOCK"
                    : "SKU_NOT_STOCKED";
            return new ReserveResult(false, request.orderId(), request.sku(), request.quantity(), reason);
        }

        reservationRepository.saveAndFlush(new Reservation(request.orderId(), request.sku(), request.quantity()));
        return new ReserveResult(true, request.orderId(), request.sku(), request.quantity(), "OK");
    }

    static ReserveResult replay(Reservation reservation) {
        return new ReserveResult(
                reservation.getStatus().name().equals("HELD"),
                reservation.getOrderId(),
                reservation.getSku(),
                reservation.getQuantity(),
                "ALREADY_" + reservation.getStatus().name());
    }
}