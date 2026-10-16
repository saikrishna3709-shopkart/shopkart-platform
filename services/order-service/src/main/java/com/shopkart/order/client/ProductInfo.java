package com.shopkart.order.client;

import java.math.BigDecimal;

public record ProductInfo(String sku, BigDecimal price, boolean active) {
}