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
 * BUOC VA 1 (Rule 1): Va BUG-1 bang Compensating Transaction chay nguoc (LIFO).
 */
class Rule1_AtomicityAndCompensationTest {

    private FakeSagaRestTemplate fake;
    private SagaOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        fake = new FakeSagaRestTemplate();
        orchestrator = new SagaOrchestratorService(fake, FakeSagaRestTemplate.ORDER,
                FakeSagaRestTemplate.INV, FakeSagaRestTemplate.PAY, 3);
    }

    @Test
    @DisplayName("Rule 1 fixes BUG-1: Payment failure triggers LIFO compensation (release inventory -> cancel order)")
    void sagaCompensatesInReverseOrderAndRestoresState() {
        fake.paymentShouldSucceed = false;

        OrderResponse res = orchestrator.executeOrderSaga(
                new OrderRequest("CUST-POOR", "PROD-002", 1, new BigDecimal("1000.00")));

        assertEquals(OrderStatus.CANCELLED, res.getStatus());
        assertEquals(5, fake.availableStock, "Inventory must be fully restored to 5");
        assertEquals(0, fake.reservedStock);
        assertEquals(OrderStatus.CANCELLED, fake.orderStatus);

        int releaseIdx = fake.indexOf("/api/inventory/compensate");
        int cancelIdx = fake.indexOf("/cancel");
        assertTrue(releaseIdx >= 0 && cancelIdx > releaseIdx, "Must release inventory before cancelling order (LIFO)");
    }
}
