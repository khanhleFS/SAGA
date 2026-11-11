package com.microservices.saga.payment.service;

import com.microservices.saga.common.dto.PaymentRequest;
import com.microservices.saga.common.dto.PaymentResponse;
import com.microservices.saga.payment.model.CustomerWallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private enum PaymentState {
        DEBITED,
        REFUNDED
    }

    // In-memory wallet store: customerId -> CustomerWallet
    private final Map<String, CustomerWallet> walletMap = new ConcurrentHashMap<>();

    // Keep track of successful debits: orderId -> PaymentRequest
    private final Map<String, PaymentRequest> transactionHistory = new ConcurrentHashMap<>();

    // Idempotency store: orderId -> trang thai giao dich
    private final Map<String, PaymentState> paymentStates = new ConcurrentHashMap<>();

    public PaymentService() {
        initData();
    }

    public synchronized void initData() {
        walletMap.clear();
        transactionHistory.clear();
        paymentStates.clear();

        walletMap.put("CUST-RICH", new CustomerWallet("CUST-RICH", "Nguyen Van A (Rich Customer)", new BigDecimal("5000.00")));
        walletMap.put("CUST-POOR", new CustomerWallet("CUST-POOR", "Tran Van B (Insufficient Balance Demo)", new BigDecimal("50.00")));
        walletMap.put("CUST-NORMAL", new CustomerWallet("CUST-NORMAL", "Le Van C (Normal Customer)", new BigDecimal("1200.00")));

        log.info("[PAYMENT] Initial customer wallets seeded: {}", walletMap.keySet());
    }

    /**
     * SAGA Step 3: Forward Action - Debit customer wallet (Idempotent theo orderId).
     */
    public synchronized PaymentResponse debitCustomer(PaymentRequest request) {
        return debitCustomer(request, false);
    }

    public synchronized PaymentResponse debitCustomer(PaymentRequest request, boolean bypassIdempotency) {
        String orderId = request.getOrderId();
        String customerId = request.getCustomerId();
        BigDecimal amount = request.getAmount();

        log.info("[SAGA FORWARD - PAYMENT] Request for Order: {}, Customer: {}, Amount: ${}", orderId, customerId, amount);

        if (!bypassIdempotency && orderId != null && paymentStates.containsKey(orderId)) {
            PaymentState state = paymentStates.get(orderId);
            log.info("[PAYMENT IDEMPOTENT] Order {} already processed (state={}). Skipping duplicate debit.", orderId, state);
            return new PaymentResponse(orderId, customerId, amount, true,
                    "Idempotent OK: Payment already processed for order " + orderId + " (" + state + ")");
        }

        CustomerWallet wallet = walletMap.get(customerId);
        if (wallet == null) {
            return new PaymentResponse(orderId, customerId, amount, false, "Customer wallet not found: " + customerId);
        }

        if (wallet.getBalance().compareTo(amount) < 0) {
            log.warn("[SAGA FORWARD - PAYMENT] FAILED - Insufficient balance for {}. Required: ${}, Available: ${}",
                    customerId, amount, wallet.getBalance());
            return new PaymentResponse(orderId, customerId, amount, false,
                    "Insufficient balance. Current: $" + wallet.getBalance() + ", Required: $" + amount);
        }

        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);

        if (orderId != null) {
            transactionHistory.put(orderId, request);
            paymentStates.put(orderId, PaymentState.DEBITED);
        }

        log.info("[SAGA FORWARD - PAYMENT] SUCCESS - Debited ${} from Customer {}. New Balance: ${}",
                amount, customerId, newBalance);

        return new PaymentResponse(orderId, customerId, amount, true, "Payment processed successfully. New balance: $" + newBalance);
    }

    /**
     * SAGA Step 3 Undo: COMPENSATING TRANSACTION - Refund customer wallet (Idempotent theo orderId).
     */
    public synchronized PaymentResponse compensateRefund(PaymentRequest request) {
        String orderId = request.getOrderId();
        String customerId = request.getCustomerId();

        log.warn("[SAGA COMPENSATION - PAYMENT] TRIGGERED for Order: {}, Customer: {}", orderId, customerId);

        if (orderId == null || paymentStates.get(orderId) != PaymentState.DEBITED) {
            log.warn("[SAGA COMPENSATION - PAYMENT] IDEMPOTENT NO-OP - No active debit to refund for Order: {}", orderId);
            return new PaymentResponse(orderId, customerId, BigDecimal.ZERO, true, "No payment to refund (Idempotent OK)");
        }

        PaymentRequest debited = transactionHistory.remove(orderId);
        BigDecimal refundAmount = (debited != null && debited.getAmount() != null)
                ? debited.getAmount()
                : request.getAmount();

        CustomerWallet wallet = walletMap.get(customerId);
        if (wallet != null && refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newBalance = wallet.getBalance().add(refundAmount);
            wallet.setBalance(newBalance);
            paymentStates.put(orderId, PaymentState.REFUNDED);

            log.info("[SAGA COMPENSATION - PAYMENT] SUCCESS - Refunded ${} to Customer {}. Restored Balance: ${}",
                    refundAmount, customerId, newBalance);
            return new PaymentResponse(orderId, customerId, refundAmount, true,
                    "Refund compensation successful. Restored balance: $" + newBalance);
        }

        return new PaymentResponse(orderId, customerId, BigDecimal.ZERO, true, "No payment to refund (Idempotent OK)");
    }

    public List<CustomerWallet> getAllWallets() {
        return new ArrayList<>(walletMap.values());
    }

    public CustomerWallet getWallet(String customerId) {
        return walletMap.get(customerId);
    }
}
