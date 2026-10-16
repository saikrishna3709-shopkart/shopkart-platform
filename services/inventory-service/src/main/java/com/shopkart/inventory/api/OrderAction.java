package com.shopkart.inventory.api;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OrderAction(@JsonProperty("order_id") @NotBlank @Size(max = 36) String orderId) {
}