package com.shopkart.inventory.api;

import jakarta.validation.constraints.Min;

public record RestockRequest(@Min(1) int quantity) {
}