package com.shopkart.inventory.stock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InventorySeederTest {

    @Test
    void seedsMatchingStockForAllStorefrontProducts() {
        StockRepository repository = mock(StockRepository.class);
        when(repository.existsBySku(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        new InventorySeeder(repository).run(null);

        ArgumentCaptor<List<StockItem>> stock = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(stock.capture());
        assertThat(stock.getValue()).hasSize(6);
        assertThat(stock.getValue()).allMatch(item -> item.getAvailable() == 25 && item.getReserved() == 0);
        verify(repository).saveAll(anyList());
    }
}