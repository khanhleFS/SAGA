package com.microservices.saga.payment;

import com.microservices.saga.common.dto.PaymentRequest;
import com.microservices.saga.common.dto.PaymentResponse;
import com.microservices.saga.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUOC VA 3 (Rule 3): Va BUG-3 bang Idempotency Key (orderId) cho Debit va Refund.
 */
class Rule3_PaymentIdempotencyTest {

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService();
    }

    @Test
    @DisplayName("BUG-3 (Without Idempotency): Duplicate debit charges customer twice")
    void bug3_withoutIdempotencyChargesTwice() {
        PaymentRequest req = new PaymentRequest("ORD-DUP", "CUST-RICH", new BigDecimal("2000.00"));

        paymentService.debitCustomer(req, true);
        paymentService.debitCustomer(req, true);

        assertEquals(new BigDecimal("1000.00"), paymentService.getWallet("CUST-RICH").getBalance(),
                "BUG-3: Customer was charged $4,000 instead of $2,000!");
    }

    @Test
    @DisplayName("Rule 3 (With Idempotency): Duplicate debit and duplicate refund each apply only once")
    void rule3_debitAndRefundAreIdempotent() {
        PaymentRequest req = new PaymentRequest("ORD-IDEMP-PAY", "CUST-RICH", new BigDecimal("2000.00"));

        PaymentResponse d1 = paymentService.debitCustomer(req);
        PaymentResponse d2 = paymentService.debitCustomer(req);
        PaymentResponse d3 = paymentService.debitCustomer(req);

        assertTrue(d1.isSuccess() && d2.isSuccess() && d3.isSuccess());
        assertEquals(new BigDecimal("3000.00"), paymentService.getWallet("CUST-RICH").getBalance(),
                "Debit 3 times for same orderId must deduct $2,000 once");

        paymentService.compensateRefund(req);
        paymentService.compensateRefund(req);
        paymentService.compensateRefund(req);

        assertEquals(new BigDecimal("5000.00"), paymentService.getWallet("CUST-RICH").getBalance(),
                "Refund 3 times for same orderId must restore $2,000 once (never over-refund)");
    }
}
