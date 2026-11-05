package com.microservices.saga.common.dto;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    COMPLETED,
    CANCELLED
}

