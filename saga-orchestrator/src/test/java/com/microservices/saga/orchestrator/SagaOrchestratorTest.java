package com.microservices.saga.orchestrator;

import com.microservices.saga.common.dto.*;
import com.microservices.saga.orchestrator.service.SagaOrchestratorService;
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

    private static final String ORDER = "http://order";
    private static final String INV = "http://inv";
    private static final String PAY = "http://pay";

    private TestableRestTemplate fake;
    private SagaOrchestratorService orchestrator;

    // Lightweight fake: no reflection/bytebuddy agents needed
    static class TestableRestTemplate extends RestTemplate {
        final List<String> calledUrls = new ArrayList<>();
        InventoryResponse reserveResponse;
        PaymentResponse paymentResponse;
        boolean orderServiceDown = false;
        boolean confirmOrderFails = false;

        @Override
        @SuppressWarnings("unchecked")
        public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables)
                throws RestClientException {
            calledUrls.add(url);
            if (url.equals(ORDER + "/api/order")) {
                if (orderServiceDown) throw new RestClientException("order service down");
                return (T) new OrderResponse("ORD-1", "C", "P", 1, BigDecimal.ONE, OrderStatus.PENDING, "created");
            } else if (url.endsWith("/confirm") && url.startsWith(ORDER)) {
                OrderStatus s = confirmOrderFails ? OrderStatus.PENDING : OrderStatus.COMPLETED;
                return (T) new OrderResponse("ORD-1", "C", "P", 1, BigDecimal.ONE, s, "confirm");
            } else if (url.contains("/cancel") && url.startsWith(ORDER)) {
                return (T) new OrderResponse("ORD-1", "C", "P", 1, BigDecimal.ONE, OrderStatus.CANCELLED, "cancelled");
            } else if (url.contains("/api/inventory/reserve")) {
                return (T) reserveResponse;
            } else if (url.contains("/api/inventory/compensate")) {
                return (T) new InventoryResponse("ORD-1", "P", 1, true, "released");
            } else if (url.contains("/api/inventory/confirm")) {
                return (T) new InventoryResponse("ORD-1", "P", 1, true, "OK");
            } else if (url.contains("/api/payment/debit")) {
                return (T) paymentResponse;
            } else if (url.contains("/api/payment/compensate")) {
                return (T) new PaymentResponse("ORD-1", "C", BigDecimal.ONE, true, "refunded");
            }
            return null;
        }

        boolean called(String part) {
            return calledUrls.stream().anyMatch(u -> u.contains(part));
        }
    }

    @BeforeEach
    void setUp() {
        fake = new TestableRestTemplate();
        orchestrator = new SagaOrchestratorService(fake, ORDER, INV, PAY);
    }

    private OrderRequest request() {
        return new OrderRequest("CUST", "PROD", 1, new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Happy path: order -> inventory -> payment -> confirm, no compensation")
    void happyPath() {
        fake.reserveResponse = new InventoryResponse("ORD-1", "PROD", 1, true, "reserved");
        fake.paymentResponse = new PaymentResponse("ORD-1", "CUST", new BigDecimal("100"), true, "paid");

        OrderResponse res = orchestrator.executeOrderSaga(request());

        assertEquals(OrderStatus.COMPLETED, res.getStatus());
        assertTrue(fake.called("/confirm"));
        assertFalse(fake.called("/cancel"));
        assertFalse(fake.called("/api/inventory/compensate"));
        assertFalse(fake.called("/api/payment/compensate"));
    }

    @Test
    @DisplayName("Payment fails: release inventory then cancel order (reverse order)")
    void paymentFailureCompensatesInReverseOrder() {
        fake.reserveResponse = new InventoryResponse("ORD-1", "PROD", 1, true, "reserved");
        fake.paymentResponse = new PaymentResponse("ORD-1", "CUST", new BigDecimal("100"), false, "Insufficient balance");

        OrderResponse res = orchestrator.executeOrderSaga(request());

        assertEquals(OrderStatus.CANCELLED, res.getStatus());
        assertFalse(fake.called("/api/payment/compensate"), "Payment never succeeded, nothing to refund");
        int release = indexOf("/api/inventory/compensate");
        int cancel = indexOf("/cancel");
        assertTrue(release >= 0 && cancel >= 0, "Both compensations must run");
        assertTrue(release < cancel, "Inventory release must run before order cancel (LIFO)");
    }

    @Test
    @DisplayName("Out of stock: only order is cancelled, payment never called")
    void inventoryFailureCancelsOrderOnly() {
        fake.reserveResponse = new InventoryResponse("ORD-1", "PROD", 1, false, "Out of stock");

        OrderResponse res = orchestrator.executeOrderSaga(request());

        assertEquals(OrderStatus.CANCELLED, res.getStatus());
        assertFalse(fake.called("/api/payment/debit"));
        assertFalse(fake.called("/api/inventory/compensate"));
        assertTrue(fake.called("/cancel"));
    }

    @Test
    @DisplayName("Confirm order fails after payment: refund, release inventory, cancel order")
    void confirmFailureRefundsPayment() {
        fake.reserveResponse = new InventoryResponse("ORD-1", "PROD", 1, true, "reserved");
        fake.paymentResponse = new PaymentResponse("ORD-1", "CUST", new BigDecimal("100"), true, "paid");
        fake.confirmOrderFails = true;

        OrderResponse res = orchestrator.executeOrderSaga(request());

        assertEquals(OrderStatus.CANCELLED, res.getStatus());
        int refund = indexOf("/api/payment/compensate");
        int release = indexOf("/api/inventory/compensate");
        int cancel = indexOf("/cancel");
        assertTrue(refund >= 0 && release >= 0 && cancel >= 0);
        assertTrue(refund < release && release < cancel, "Compensation must run in reverse order");
    }

    @Test
    @DisplayName("Order service down: saga fails at step 1, nothing else is called")
    void orderServiceDown() {
        fake.orderServiceDown = true;

        OrderResponse res = orchestrator.executeOrderSaga(request());

        assertEquals(OrderStatus.CANCELLED, res.getStatus());
        assertFalse(fake.called("/api/inventory/reserve"));
        assertFalse(fake.called("/api/payment/debit"));
    }

    private int indexOf(String part) {
        for (int i = 0; i < fake.calledUrls.size(); i++) {
            if (fake.calledUrls.get(i).contains(part)) return i;
        }
        return -1;
    }
}
