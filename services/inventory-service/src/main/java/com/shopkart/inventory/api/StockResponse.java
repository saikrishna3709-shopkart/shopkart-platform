package com.shopkart.inventory.api;

import com.shopkart.inventory.stock.StockItem;

public record StockResponse(String sku, int available, int reserved) {

    public static StockResponse from(StockItem item) {
        return new StockResponse(item.getSku(), item.getAvailable(), item.getReserved());
    }
}