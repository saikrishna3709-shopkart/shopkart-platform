package com.shopkart.order.client;

import java.math.BigDecimal;
import java.util.Optional;

public interface DownstreamClient {

    Optional<ProductInfo> getProduct(String sku);

    ReserveOutcome reserveStock(String orderId, String sku, int quantity);

    void releaseStock(String orderId);

    void commitStock(String orderId);

    PaymentOutcome charge(String orderId, BigDecimal amount, String cardToken);

    boolean notify(
            String orderId, String customerId, String event, String email, String phone,
            BigDecimal amount, String trackingId);
}