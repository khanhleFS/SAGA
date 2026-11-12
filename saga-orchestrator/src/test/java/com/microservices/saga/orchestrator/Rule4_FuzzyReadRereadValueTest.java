package com.microservices.saga.orchestrator;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderResponse;
import com.microservices.saga.common.dto.OrderStatus;
import com.microservices.saga.orchestrator.service.SagaOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUOC VA 4 (Countermeasure: Reread Value / Optimistic Offline Lock):
 * Phat hien Fuzzy (Nonrepeatable) Read khi gia san pham bi thay doi giua 2 lan doc ($1000 -> $1500),
 * chan thanh toan sai gia va kich hoat LIFO Compensation (Release Inventory -> Cancel Order).
 */
class Rule4_FuzzyReadRereadValueTest {

    private FakeSagaRestTemplate fake;
    private SagaOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        fake = new FakeSagaRestTemplate();
        orchestrator = new SagaOrchestratorService(fake, FakeSagaRestTemplate.ORDER,
                FakeSagaRestTemplate.INV, FakeSagaRestTemplate.PAY, 3);
    }

    @Test
    @DisplayName("Countermeasure 4 fixes BUG-4 (Fuzzy Read): Reread Value detects mid-saga price change ($1000->$1500), aborts payment and compensates LIFO")
    void rereadValueDetectsFuzzyReadAndCompensates() {
        OrderResponse res = orchestrator.executeSagaFuzzyReadCheck(
                new OrderRequest("CUST-RICH", "PROD-002", 1, new BigDecimal("1000.00")));

        assertEquals(OrderStatus.CANCELLED, res.getStatus(), "Saga must abort and cancel when Reread Value detects Fuzzy Read");
        assertEquals(new BigDecimal("5000.00"), fake.walletBalance, "Wallet must NOT be debited when price changed mid-saga");
        assertEquals(5, fake.availableStock, "Reserved stock must be released back to availableStock (5)");
        assertEquals(0, fake.reservedStock, "Semantic lock reservedStock must be cleared (0)");
        assertFalse(fake.called("/api/payment/debit"), "Payment debit must be blocked upon Reread Value mismatch");
        assertTrue(fake.called("/api/inventory/compensate"), "Inventory compensation must run");
        assertTrue(fake.called("/cancel"), "Order cancellation must run");
    }
}
