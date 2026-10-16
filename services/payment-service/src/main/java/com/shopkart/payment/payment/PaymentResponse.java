package com.shopkart.payment.payment;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentResponse(
    @JsonProperty("payment_id") String paymentId,
    @JsonProperty("order_id") String orderId,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        PaymentStatus status,
        String reason) {

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getReason());
    }
}