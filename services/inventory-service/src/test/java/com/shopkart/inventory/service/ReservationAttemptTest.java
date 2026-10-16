package com.shopkart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.shopkart.inventory.api.ReserveRequest;
import com.shopkart.inventory.api.ReserveResult;
import com.shopkart.inventory.reservation.Reservation;
import com.shopkart.inventory.reservation.ReservationRepository;
import com.shopkart.inventory.stock.StockRepository;

import org.junit.jupiter.api.Test;

class ReservationAttemptTest {

    @Test
    void reservationCompletedWhileCasWaitedIsAnIdempotentReplay() {
        StockRepository stockRepository = mock(StockRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        ReserveRequest request = new ReserveRequest("order-1", "TSHIRT-001", 1);
        Reservation winner = new Reservation("order-1", "TSHIRT-001", 1);

        when(reservationRepository.findByOrderIdAndSku("order-1", "TSHIRT-001"))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(stockRepository.reserve("TSHIRT-001", 1)).thenReturn(0);

        ReserveResult result = new ReservationAttempt(stockRepository, reservationRepository).execute(request);

        assertThat(result.reserved()).isTrue();
        assertThat(result.reason()).isEqualTo("ALREADY_HELD");
    }
}