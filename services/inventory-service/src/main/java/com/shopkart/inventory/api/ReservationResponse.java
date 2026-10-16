package com.shopkart.inventory.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopkart.inventory.reservation.Reservation;
import com.shopkart.inventory.reservation.ReservationStatus;

public record ReservationResponse(
        @JsonProperty("order_id") String orderId,
        String sku,
        int quantity,
        ReservationStatus status) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getOrderId(),
                reservation.getSku(),
                reservation.getQuantity(),
                reservation.getStatus());
    }
}