package com.shopkart.catalog.product;

public class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku, Throwable cause) {
        super("sku " + sku + " already exists", cause);
    }
}