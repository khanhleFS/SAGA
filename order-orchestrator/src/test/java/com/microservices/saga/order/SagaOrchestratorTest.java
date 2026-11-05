package com.microservices.saga.order;

import com.microservices.saga.common.dto.*;
import com.microservices.saga.order.service.SagaOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SagaOrchestratorTest {

    private TestableRestTemplate fakeRestTemplate;
    private SagaOrchestratorService orchestratorService;

    // Lightweight mock without reflection/bytebuddy agents
    static class TestableRestTemplate extends RestTemplate {
        final List<String> calledUrls = new ArrayList<>();
        InventoryResponse mockReserveResponse;
        PaymentResponse mockPaymentResponse;
        InventoryResponse mockCompensateResponse;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) 
                throws RestClientException {
            calledUrls.add(url);
            if (url.contains("/api/inventory/reserve")) {
                return (T) mockReserveResponse;
            } else if (url.contains("/api/payment/debit")) {
                return (T) mockPaymentResponse;
            } else if (url.contains("/api/inventory/compensate")) {
                return (T) mockCompensateResponse;
            } else if (url.contains("/api/inventory/confirm")) {
                return (T) new InventoryResponse("CONFIRM", "P1", 1, true, "OK");
            }
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        fakeRestTemplate = new TestableRestTemplate();
        orchestratorService = new SagaOrchestratorService(
                fakeRestTemplate,
                "http://localhost:8082",
                "http://localhost:8083"
        );
    }

    @Test
    @DisplayName("Scenario 1: Happy Path - All distributed steps succeed")
    void testHappyPath() {
        // Given
        OrderRequest request = new OrderRequest("CUST-RICH", "PROD-001", 1, new BigDecimal("2000.00"));
        fakeRestTemplate.mockReserveResponse = new InventoryResponse("ORD-1", "PROD-001", 1, true, "Stock reserved");
        fakeRestTemplate.mockPaymentResponse = new PaymentResponse("ORD-1", "CUST-RICH", new BigDecimal("2000.00"), true, "Paid");

        // When
        OrderResponse response = orchestratorService.executeOrderSaga(request);

        // Then
        assertEquals(OrderStatus.COMPLETED, response.getStatus());
        assertTrue(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/inventory/reserve")));
        assertTrue(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/payment/debit")));
        assertFalse(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/inventory/compensate")));
    }

    @Test
    @DisplayName("Scenario 2: SAGA Compensation - Payment fails and triggers rollback of inventory")
    void testPaymentFailureTriggersCompensation() {
        // Given
        OrderRequest request = new OrderRequest("CUST-POOR", "PROD-002", 1, new BigDecimal("1000.00"));
        fakeRestTemplate.mockReserveResponse = new InventoryResponse("ORD-2", "PROD-002", 1, true, "Stock reserved");
        fakeRestTemplate.mockPaymentResponse = new PaymentResponse("ORD-2", "CUST-POOR", new BigDecimal("1000.00"), false, "Insufficient balance");
        fakeRestTemplate.mockCompensateResponse = new InventoryResponse("ORD-2", "PROD-002", 1, true, "Compensated");

        // When
        OrderResponse response = orchestratorService.executeOrderSaga(request);

        // Then
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertTrue(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/inventory/reserve")));
        assertTrue(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/payment/debit")));
        // CRITICAL SAGA CHECK: Verify Compensation was called!
        assertTrue(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/inventory/compensate")),
                "Compensating transaction must be invoked to release inventory");
    }

    @Test
    @DisplayName("Scenario 3: Inventory Out of Stock - Early termination without compensation")
    void testInventoryOutOfStock() {
        // Given
        OrderRequest request = new OrderRequest("CUST-RICH", "PROD-OUT-OF-STOCK", 1, new BigDecimal("500.00"));
        fakeRestTemplate.mockReserveResponse = new InventoryResponse("ORD-3", "PROD-OUT-OF-STOCK", 1, false, "Out of stock");

        // When
        OrderResponse response = orchestratorService.executeOrderSaga(request);

        // Then
        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        assertTrue(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/inventory/reserve")));
        // Payment should NOT be called
        assertFalse(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/payment/debit")));
        // Compensation should NOT be called
        assertFalse(fakeRestTemplate.calledUrls.stream().anyMatch(u -> u.contains("/api/inventory/compensate")));
    }
}
