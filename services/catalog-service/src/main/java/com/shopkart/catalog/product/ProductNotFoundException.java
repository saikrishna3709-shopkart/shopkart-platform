package com.shopkart.catalog.product;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String sku) {
        super("product " + sku + " not found");
    }
}