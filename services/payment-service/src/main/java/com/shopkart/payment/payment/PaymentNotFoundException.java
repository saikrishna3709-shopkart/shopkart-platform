package com.shopkart.payment.payment;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String orderId) {
        super("no payment for order " + orderId);
    }
}