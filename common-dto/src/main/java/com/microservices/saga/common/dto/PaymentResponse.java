package com.microservices.saga.common.dto;

import java.math.BigDecimal;

public class PaymentResponse {
    private String orderId;
    private String customerId;
    private BigDecimal amount;
    private boolean success;
    private String message;

    public PaymentResponse() {
    }

    public PaymentResponse(String orderId, String customerId, BigDecimal amount, boolean success, String message) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.success = success;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

