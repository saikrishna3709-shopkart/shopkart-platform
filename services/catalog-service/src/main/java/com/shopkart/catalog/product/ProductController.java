package com.shopkart.catalog.product;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
        return service.create(request);
    }

    @GetMapping("/products")
    public List<ProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(name = "active_only", defaultValue = "true") boolean activeOnly) {
        return service.list(category, brand, activeOnly);
    }

    @GetMapping("/products/{sku}")
    public ProductResponse get(@PathVariable String sku) {
        return service.get(sku);
    }

    @PatchMapping("/products/{sku}/price")
    public ProductResponse updatePrice(
            @PathVariable String sku,
            @Valid @RequestBody PriceUpdateRequest request) {
        return service.updatePrice(sku, request);
    }

    @DeleteMapping("/products/{sku}")
    public ProductResponse deactivate(@PathVariable String sku) {
        return service.deactivate(sku);
    }
}