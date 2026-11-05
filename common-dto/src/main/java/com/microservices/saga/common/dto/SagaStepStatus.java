package com.microservices.saga.common.dto;

public enum SagaStepStatus {
    PENDING,
    SUCCESS,
    FAILED,
    COMPENSATING,
    COMPENSATED
}

