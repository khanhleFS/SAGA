package com.microservices.saga.payment.model;

import java.math.BigDecimal;

public class CustomerWallet {
    private String customerId;
    private String customerName;
    private BigDecimal balance;

    public CustomerWallet() {
    }

    public CustomerWallet(String customerId, String customerName, BigDecimal balance) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.balance = balance;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }
}

