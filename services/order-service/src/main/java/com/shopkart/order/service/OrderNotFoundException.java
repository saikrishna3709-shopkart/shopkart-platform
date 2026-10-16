package com.shopkart.order.service;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderId) {
        super("order " + orderId + " not found");
    }
}