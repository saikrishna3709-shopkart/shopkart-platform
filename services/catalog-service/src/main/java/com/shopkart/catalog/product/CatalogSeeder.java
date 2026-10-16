package com.shopkart.catalog.product;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shopkart.seed-data", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder implements ApplicationRunner {

    private final ProductRepository repository;

    public CatalogSeeder(ProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<Product> products = List.of(
                product("TSHIRT-001", "Everyday Cotton Tee", "Soft heavyweight cotton with a clean regular fit.",
                        "clothing", "Northstar", "799.00"),
                product("SHOE-001", "Velocity Street Sneakers", "Cushioned everyday sneakers built for long city walks.",
                        "footwear", "Stride", "2499.00"),
                product("AUDIO-001", "Pulse Wireless Headphones", "Comfortable over-ear sound with focused listening modes.",
                        "electronics", "Sonic", "3499.00"),
                product("WATCH-001", "Minimal Steel Watch", "A precise steel timepiece with an understated dial.",
                        "accessories", "Aster", "4299.00"),
                product("BAG-001", "Transit Daypack", "A weather-ready daypack with a padded laptop compartment.",
                        "bags", "Roam", "1899.00"),
                product("SUN-001", "Coastline Sunglasses", "Polarized lenses in a lightweight everyday frame.",
                        "accessories", "Sol", "1299.00"));

        List<Product> missing = products.stream()
                .filter(product -> repository.findBySku(product.getSku()).isEmpty())
                .toList();
        if (!missing.isEmpty()) {
            repository.saveAll(missing);
        }
    }

    private Product product(
            String sku, String name, String description, String category, String brand, String price) {
        return new Product(sku, name, description, category, brand, new BigDecimal(price));
    }
}