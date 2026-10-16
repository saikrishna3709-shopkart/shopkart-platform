package com.shopkart.inventory.stock;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<StockItem, Long> {

    Optional<StockItem> findBySku(String sku);

    boolean existsBySku(String sku);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
            SET available = available - :quantity,
                reserved = reserved + :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE sku = :sku AND available >= :quantity
            """, nativeQuery = true)
    int reserve(@Param("sku") String sku, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
            SET available = available + :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE sku = :sku
            """, nativeQuery = true)
    int restock(@Param("sku") String sku, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
            SET available = available + :quantity,
                reserved = reserved - :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE sku = :sku AND reserved >= :quantity
            """, nativeQuery = true)
    int release(@Param("sku") String sku, @Param("quantity") int quantity);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE stock_items
            SET reserved = reserved - :quantity,
                updated_at = CURRENT_TIMESTAMP
            WHERE sku = :sku AND reserved >= :quantity
            """, nativeQuery = true)
    int commit(@Param("sku") String sku, @Param("quantity") int quantity);
}