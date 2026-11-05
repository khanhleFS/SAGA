package com.microservices.saga.common.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderResponse {
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private BigDecimal amount;
    private OrderStatus status;
    private String message;
    private List<SagaStepLog> sagaLogs = new ArrayList<>();

    public OrderResponse() {
    }

    public OrderResponse(String orderId, String customerId, String productId, int quantity, 
                         BigDecimal amount, OrderStatus status, String message) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.message = message;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
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

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<SagaStepLog> getSagaLogs() {
        return sagaLogs;
    }

    public void setSagaLogs(List<SagaStepLog> sagaLogs) {
        this.sagaLogs = sagaLogs;
    }
}

