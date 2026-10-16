package com.shopkart.order.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record OrderCreateRequest(
        @JsonProperty("customer_id") @NotBlank @Size(max = 36) String customerId,
        @NotEmpty List<@Valid OrderItemRequest> items,
        @JsonProperty("card_token") @NotBlank @Size(max = 64) String cardToken,
        @Size(max = 120) String email,
        @Size(max = 20) String phone) {
}