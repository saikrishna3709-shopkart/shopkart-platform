package com.shopkart.inventory.service;

public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(String sku) {
        super("sku " + sku + " not stocked");
    }
}