package com.microservices.saga.orchestrator;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderResponse;
import com.microservices.saga.common.dto.OrderStatus;
import com.microservices.saga.orchestrator.service.SagaOrchestratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUOC VA 2 (Rule 2): Va BUG-2 bang Semantic Lock (PENDING / reservedStock) trong luc SAGA dang chay.
 */
class Rule2_SemanticLockIsolationTest {

    @BeforeEach
    void noop() {}

    @Test
    @DisplayName("Rule 2 fixes BUG-2: Mid-flight observation sees reservedStock=1 & Order=PENDING before Confirm")
    void midFlightStateShowsSemanticLock() {
        AtomicInteger observedReservedMidFlight = new AtomicInteger(-1);
        AtomicReference<OrderStatus> observedOrderMidFlight = new AtomicReference<>();

        FakeSagaRestTemplate fake = new FakeSagaRestTemplate() {
            @Override
            public <T> T postForObject(String url, Object request, Class<T> responseType, Object... uriVariables) {
                if (url.startsWith(PAY + "/api/payment/debit")) {
                    // Ngay truoc luc Payment chay, kiem tra Semantic Lock
                    observedReservedMidFlight.set(this.reservedStock);
                    observedOrderMidFlight.set(this.orderStatus);
                }
                return super.postForObject(url, request, responseType, uriVariables);
            }
        };

        SagaOrchestratorService orchestrator = new SagaOrchestratorService(
                fake, FakeSagaRestTemplate.ORDER, FakeSagaRestTemplate.INV, FakeSagaRestTemplate.PAY, 3);

        OrderResponse res = orchestrator.executeOrderSaga(
                new OrderRequest("CUST-RICH", "PROD-001", 1, new BigDecimal("2000.00")));

        assertEquals(1, observedReservedMidFlight.get(), "Mid-flight stock must be held in reservedStock");
        assertEquals(OrderStatus.PENDING, observedOrderMidFlight.get(), "Mid-flight order must stay PENDING");
        assertEquals(OrderStatus.COMPLETED, res.getStatus());
        assertEquals(0, fake.reservedStock, "After completion, semantic lock is released");
    }
}
