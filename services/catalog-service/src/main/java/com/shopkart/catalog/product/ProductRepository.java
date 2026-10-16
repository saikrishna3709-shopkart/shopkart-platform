package com.shopkart.catalog.product;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    @Query("""
            SELECT product FROM Product product
            WHERE (:category IS NULL OR product.category = :category)
              AND (:brand IS NULL OR product.brand = :brand)
              AND (:activeOnly = false OR product.active = true)
            ORDER BY product.id
            """)
    List<Product> findProducts(
            @Param("category") String category,
            @Param("brand") String brand,
            @Param("activeOnly") boolean activeOnly);
}