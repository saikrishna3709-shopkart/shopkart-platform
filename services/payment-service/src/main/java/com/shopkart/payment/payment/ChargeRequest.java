package com.shopkart.payment.payment;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChargeRequest(
        @JsonProperty("order_id") @NotBlank @Size(max = 36) String orderId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @JsonProperty("card_token") @NotBlank @Size(max = 64) String cardToken) {
}