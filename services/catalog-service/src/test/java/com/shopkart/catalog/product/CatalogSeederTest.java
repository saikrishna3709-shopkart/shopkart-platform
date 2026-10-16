package com.shopkart.catalog.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CatalogSeederTest {

    @Test
    void seedsAllMissingStorefrontProducts() {
        ProductRepository repository = mock(ProductRepository.class);
        when(repository.findBySku(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());

        new CatalogSeeder(repository).run(null);

        ArgumentCaptor<List<Product>> products = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(products.capture());
        assertThat(products.getValue()).hasSize(6);
        assertThat(products.getValue()).extracting(Product::getSku)
                .containsExactly("TSHIRT-001", "SHOE-001", "AUDIO-001", "WATCH-001", "BAG-001", "SUN-001");
        verify(repository).saveAll(anyList());
    }
}