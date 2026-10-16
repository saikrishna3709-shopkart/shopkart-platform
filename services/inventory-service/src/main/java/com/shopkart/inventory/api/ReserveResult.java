package com.shopkart.inventory.api;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReserveResult(
        boolean reserved,
        @JsonProperty("order_id") String orderId,
        String sku,
        int quantity,
        String reason) {
}