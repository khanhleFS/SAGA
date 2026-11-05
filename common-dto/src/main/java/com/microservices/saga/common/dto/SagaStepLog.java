package com.microservices.saga.common.dto;

import java.time.LocalDateTime;

public class SagaStepLog {
    private String stepName;
    private SagaStepStatus status;
    private String details;
    private LocalDateTime timestamp;

    public SagaStepLog() {
        this.timestamp = LocalDateTime.now();
    }

    public SagaStepLog(String stepName, SagaStepStatus status, String details) {
        this.stepName = stepName;
        this.status = status;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public SagaStepStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStepStatus status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + stepName + " -> " + status + " (" + details + ")";
    }
}

