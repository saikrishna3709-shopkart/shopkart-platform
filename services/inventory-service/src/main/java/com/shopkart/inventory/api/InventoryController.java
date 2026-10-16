package com.shopkart.inventory.api;

import java.util.List;

import com.shopkart.inventory.service.InventoryService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @PostMapping("/inventory")
    @ResponseStatus(HttpStatus.CREATED)
    public StockResponse create(@Valid @RequestBody StockCreateRequest request) {
        return service.create(request);
    }

    @GetMapping("/inventory/{sku}")
    public StockResponse get(@PathVariable String sku) {
        return service.get(sku);
    }

    @PatchMapping("/inventory/{sku}/restock")
    public StockResponse restock(@PathVariable String sku, @Valid @RequestBody RestockRequest request) {
        return service.restock(sku, request);
    }

    @PostMapping("/inventory/reserve")
    public ReserveResult reserve(@Valid @RequestBody ReserveRequest request) {
        return service.reserve(request);
    }

    @PostMapping("/inventory/release")
    public List<ReservationResponse> release(@Valid @RequestBody OrderAction action) {
        return service.release(action);
    }

    @PostMapping("/inventory/commit")
    public List<ReservationResponse> commit(@Valid @RequestBody OrderAction action) {
        return service.commit(action);
    }
}