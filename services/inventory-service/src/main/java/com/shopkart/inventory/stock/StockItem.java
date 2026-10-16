package com.shopkart.inventory.stock;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Check;

@Entity
@Table(name = "stock_items",
        indexes = @Index(name = "idx_stock_items_sku", columnList = "sku", unique = true),
        uniqueConstraints = @UniqueConstraint(name = "uq_stock_items_sku", columnNames = "sku"))
@Check(constraints = "available >= 0 AND reserved >= 0")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sku;

    @Column(nullable = false)
    private int available;

    @Column(nullable = false)
    private int reserved;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected StockItem() {
    }

    public StockItem(String sku, int available) {
        this.sku = sku;
        this.available = available;
        this.reserved = 0;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public String getSku() {
        return sku;
    }

    public int getAvailable() {
        return available;
    }

    public int getReserved() {
        return reserved;
    }
}