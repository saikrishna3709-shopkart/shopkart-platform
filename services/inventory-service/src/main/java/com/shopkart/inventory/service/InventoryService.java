package com.shopkart.inventory.service;

import java.util.List;

import com.shopkart.inventory.api.OrderAction;
import com.shopkart.inventory.api.ReservationResponse;
import com.shopkart.inventory.api.ReserveRequest;
import com.shopkart.inventory.api.ReserveResult;
import com.shopkart.inventory.api.RestockRequest;
import com.shopkart.inventory.api.StockCreateRequest;
import com.shopkart.inventory.api.StockResponse;
import com.shopkart.inventory.reservation.Reservation;
import com.shopkart.inventory.reservation.ReservationRepository;
import com.shopkart.inventory.reservation.ReservationStatus;
import com.shopkart.inventory.stock.StockItem;
import com.shopkart.inventory.stock.StockRepository;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final StockRepository stockRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAttempt reservationAttempt;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    public InventoryService(
            StockRepository stockRepository,
            ReservationRepository reservationRepository,
            ReservationAttempt reservationAttempt,
            ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.stockRepository = stockRepository;
        this.reservationRepository = reservationRepository;
        this.reservationAttempt = reservationAttempt;
        this.meterRegistryProvider = meterRegistryProvider;
    }

    @Transactional
    public StockResponse create(StockCreateRequest request) {
        return StockResponse.from(stockRepository.saveAndFlush(new StockItem(request.sku(), request.quantity())));
    }

    @Transactional(readOnly = true)
    public StockResponse get(String sku) {
        return stockRepository.findBySku(sku)
                .map(StockResponse::from)
                .orElseThrow(() -> new StockNotFoundException(sku));
    }

    @Transactional
    public StockResponse restock(String sku, RestockRequest request) {
        if (stockRepository.restock(sku, request.quantity()) == 0) {
            throw new StockNotFoundException(sku);
        }
        return get(sku);
    }

    public ReserveResult reserve(ReserveRequest request) {
        try {
            ReserveResult result = reservationAttempt.execute(request);
            recordReservation(result.reason());
            return result;
        } catch (DataIntegrityViolationException exception) {
            Reservation existing = reservationRepository.findByOrderIdAndSku(request.orderId(), request.sku())
                    .orElseThrow(() -> exception);
            recordReservation("IDEMPOTENT_REPLAY");
            return ReservationAttempt.replay(existing);
        }
    }

    @Transactional
    public List<ReservationResponse> release(OrderAction action) {
        List<Reservation> held = reservationRepository.findForUpdate(action.orderId(), ReservationStatus.HELD);
        for (Reservation reservation : held) {
            if (stockRepository.release(reservation.getSku(), reservation.getQuantity()) != 1) {
                throw new IllegalStateException("reserved stock invariant violated");
            }
            reservation.release();
        }
        return allReservations(action.orderId());
    }

    @Transactional
    public List<ReservationResponse> commit(OrderAction action) {
        List<Reservation> held = reservationRepository.findForUpdate(action.orderId(), ReservationStatus.HELD);
        for (Reservation reservation : held) {
            if (stockRepository.commit(reservation.getSku(), reservation.getQuantity()) != 1) {
                throw new IllegalStateException("reserved stock invariant violated");
            }
            reservation.commit();
        }
        return allReservations(action.orderId());
    }

    private List<ReservationResponse> allReservations(String orderId) {
        return reservationRepository.findByOrderIdOrderById(orderId).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    private void recordReservation(String outcome) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry != null) {
            registry.counter("shopkart.reservations", "outcome", outcome).increment();
            if ("INSUFFICIENT_STOCK".equals(outcome)) {
                registry.counter("shopkart.oversell.blocked").increment();
            }
        }
    }
}