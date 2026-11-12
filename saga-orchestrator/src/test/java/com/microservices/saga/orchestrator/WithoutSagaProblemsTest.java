package com.microservices.saga.orchestrator;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderStatus;
import com.microservices.saga.orchestrator.service.SagaOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PHAN 1 (PROBLEM): Chay luong Naive chua co SAGA de tai hien Partial Commit (BUG-1) & Dirty Read (BUG-2).
 */
class WithoutSagaProblemsTest {

    private FakeSagaRestTemplate fake;
    private SagaOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        fake = new FakeSagaRestTemplate();
        orchestrator = new SagaOrchestratorService(fake, FakeSagaRestTemplate.ORDER,
                FakeSagaRestTemplate.INV, FakeSagaRestTemplate.PAY, 3);
    }

    @Test
    @DisplayName("BUG-1 (Partial Commit): Payment fails in Naive flow -> inventory stays missing (5->4) and Order stuck PENDING")
    void bug1_partialCommitLeavesInconsistentData() {
        fake.paymentShouldSucceed = false;

        orchestrator.executeWithoutSaga(
                new OrderRequest("CUST-POOR", "PROD-002", 1, new BigDecimal("1000.00")),
                "partial_commit", 0);

        assertEquals(4, fake.availableStock, "BUG-1: Available stock was reduced and never restored");
        assertEquals(OrderStatus.PENDING, fake.orderStatus, "BUG-1: Order left stuck in PENDING");
        assertFalse(fake.called("/api/inventory/compensate"));
    }

    @Test
    @DisplayName("BUG-2 (Dirty Read): Concurrent reader sees stock deducted (5->4) with reservedStock = 0 while order is still unpaid")
    void bug2_noSemanticLock() {
        fake.paymentShouldSucceed = false;

        orchestrator.executeWithoutSaga(
                new OrderRequest("CUST-POOR", "PROD-002", 1, new BigDecimal("1000.00")),
                "no_semantic_lock", 0);

        assertEquals(4, fake.availableStock);
        assertEquals(0, fake.reservedStock, "BUG-2: No reservedStock lock was created");
    }

    @Test
    @DisplayName("BUG-4 (Fuzzy / Nonrepeatable Read): Price changes mid-saga ($1000->$1500) -> Order records $1000 (Read #1) but Wallet debits $1500 (Read #2)")
    void bug4_fuzzyReadCausesInconsistentOrderAndPayment() {
        orchestrator.executeWithoutSaga(
                new OrderRequest("CUST-RICH", "PROD-002", 1, new BigDecimal("1000.00")),
                "fuzzy_read", 0);

        assertEquals(new BigDecimal("1000.00"), fake.orderAmount, "Read #1 at Order creation recorded $1000.00");
        assertEquals(new BigDecimal("1500.00"), fake.productPrice, "Concurrent transaction changed price to $1500.00");
        assertEquals(new BigDecimal("3500.00"), fake.walletBalance,
                "BUG-4 (Fuzzy Read): Wallet was debited $1500.00 (Read #2) instead of $1000.00 (Read #1)!");
        assertEquals(OrderStatus.COMPLETED, fake.orderStatus);
    }
}
