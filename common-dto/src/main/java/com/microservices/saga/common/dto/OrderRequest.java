package com.microservices.saga.common.dto;

import java.math.BigDecimal;

public class OrderRequest {
    private String customerId;
    private String productId;
    private int quantity;
    private BigDecimal amount;

    public OrderRequest() {
    }

    public OrderRequest(String customerId, String productId, int quantity, BigDecimal amount) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}

