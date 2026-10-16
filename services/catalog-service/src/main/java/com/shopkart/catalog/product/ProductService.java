package com.shopkart.catalog.product;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        Product product = new Product(
                request.sku(),
                request.name(),
                request.normalizedDescription(),
                request.category(),
                request.normalizedBrand(),
                request.price());
        try {
            return ProductResponse.from(repository.saveAndFlush(product));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateSkuException(request.sku(), exception);
        }
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(String category, String brand, boolean activeOnly) {
        return repository.findProducts(normalizeFilter(category), normalizeFilter(brand), activeOnly).stream()
                .map(ProductResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String sku) {
        return ProductResponse.from(find(sku));
    }

    @Transactional
    public ProductResponse updatePrice(String sku, PriceUpdateRequest request) {
        Product product = find(sku);
        product.updatePrice(request.price());
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse deactivate(String sku) {
        Product product = find(sku);
        product.deactivate();
        return ProductResponse.from(product);
    }

    private Product find(String sku) {
        return repository.findBySku(sku).orElseThrow(() -> new ProductNotFoundException(sku));
    }

    private String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}