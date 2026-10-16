package com.shopkart.inventory.stock;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "shopkart.seed-data", havingValue = "true", matchIfMissing = true)
public class InventorySeeder implements ApplicationRunner {

    private static final int DEMO_QUANTITY = 25;

    private final StockRepository repository;

    public InventorySeeder(StockRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<String> skus = List.of(
                "TSHIRT-001", "SHOE-001", "AUDIO-001", "WATCH-001", "BAG-001", "SUN-001");
        List<StockItem> missing = skus.stream()
                .filter(sku -> !repository.existsBySku(sku))
                .map(sku -> new StockItem(sku, DEMO_QUANTITY))
                .toList();
        if (!missing.isEmpty()) {
            repository.saveAll(missing);
        }
    }
}