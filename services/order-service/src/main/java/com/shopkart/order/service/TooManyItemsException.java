package com.shopkart.order.service;

public class TooManyItemsException extends RuntimeException {

    public TooManyItemsException() {
        super("too many line items");
    }
}