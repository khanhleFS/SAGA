package com.microservices.saga.inventory.model;

public class ProductInventory {
    private String productId;
    private String productName;
    private int availableStock;
    private int reservedStock;

    public ProductInventory() {
    }

    public ProductInventory(String productId, String productName, int availableStock, int reservedStock) {
        this.productId = productId;
        this.productName = productName;
        this.availableStock = availableStock;
        this.reservedStock = reservedStock;
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

    public int getTotalStock() {
        return availableStock + reservedStock;
    }
}

