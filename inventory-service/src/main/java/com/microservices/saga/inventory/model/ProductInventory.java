package com.microservices.saga.inventory.model;

import java.math.BigDecimal;

public class ProductInventory {
    private String productId;
    private String productName;
    private int availableStock;
    private int reservedStock;
    private BigDecimal price;

    public ProductInventory() {
    }

    public ProductInventory(String productId, String productName, int availableStock, int reservedStock) {
        this(productId, productName, availableStock, reservedStock, defaultPriceFor(productId));
    }

    public ProductInventory(String productId, String productName, int availableStock, int reservedStock, BigDecimal price) {
        this.productId = productId;
        this.productName = productName;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
        this.price = price;
    }

    private static BigDecimal defaultPriceFor(String productId) {
        if ("PROD-001".equals(productId)) return new BigDecimal("2000.00");
        if ("PROD-002".equals(productId)) return new BigDecimal("1000.00");
        return new BigDecimal("700.00");
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getAvailableStock() {
        return availableStock;
    }

    public void setAvailableStock(int availableStock) {
        this.availableStock = availableStock;
    }

    public int getReservedStock() {
        return reservedStock;
    }

    public void setReservedStock(int reservedStock) {
        this.reservedStock = reservedStock;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getTotalStock() {
        return availableStock + reservedStock;
    }
}

