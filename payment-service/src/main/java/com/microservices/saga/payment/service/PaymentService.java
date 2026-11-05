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

    // In-memory wallet store: customerId -> CustomerWallet
    private final Map<String, CustomerWallet> walletMap = new ConcurrentHashMap<>();

    // Keep track of successful debits: orderId -> PaymentRequest (for refund compensation)
    private final Map<String, PaymentRequest> transactionHistory = new ConcurrentHashMap<>();

    public PaymentService() {
        initData();
    }

    public synchronized void initData() {
        walletMap.clear();
        transactionHistory.clear();

        // Seed demo accounts
        walletMap.put("CUST-RICH", new CustomerWallet("CUST-RICH", "Nguyen Van A (Rich Customer)", new BigDecimal("5000.00")));
        walletMap.put("CUST-POOR", new CustomerWallet("CUST-POOR", "Tran Van B (Insufficient Balance Demo)", new BigDecimal("50.00")));
        walletMap.put("CUST-NORMAL", new CustomerWallet("CUST-NORMAL", "Le Van C (Normal Customer)", new BigDecimal("1200.00")));

        log.info("[PAYMENT] Initial customer wallets seeded: {}", walletMap.keySet());
    }

    /**
     * SAGA Step 2: Forward Action - Debit customer wallet
     */
    public synchronized PaymentResponse debitCustomer(PaymentRequest request) {
        String orderId = request.getOrderId();
        String customerId = request.getCustomerId();
        BigDecimal amount = request.getAmount();

        log.info("[SAGA FORWARD - PAYMENT] Request received for Order: {}, Customer: {}, Amount: ${}", 
                orderId, customerId, amount);

        CustomerWallet wallet = walletMap.get(customerId);
        if (wallet == null) {
            log.warn("[SAGA FORWARD - PAYMENT] FAILED - Customer {} wallet not found", customerId);
            return new PaymentResponse(orderId, customerId, amount, false, "Customer wallet not found: " + customerId);
        }

        if (wallet.getBalance().compareTo(amount) < 0) {
            log.warn("[SAGA FORWARD - PAYMENT] FAILED - Insufficient balance for customer {}. Required: ${}, Available: ${}", 
                    customerId, amount, wallet.getBalance());
            return new PaymentResponse(orderId, customerId, amount, false, 
                    "Insufficient balance. Current: $" + wallet.getBalance() + ", Required: $" + amount);
        }

        // Deduct balance
        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);

        // Record transaction
        transactionHistory.put(orderId, request);

        log.info("[SAGA FORWARD - PAYMENT] SUCCESS - Debited ${} from Customer {}. New Balance: ${}", 
                amount, customerId, newBalance);

        return new PaymentResponse(orderId, customerId, amount, true, "Payment processed successfully. New balance: $" + newBalance);
    }

    /**
     * SAGA Step 2 Undo: COMPENSATING TRANSACTION - Refund customer wallet
     */
    public synchronized PaymentResponse compensateRefund(PaymentRequest request) {
        String orderId = request.getOrderId();
        String customerId = request.getCustomerId();
        BigDecimal amount = request.getAmount();

        log.warn("[SAGA COMPENSATION - PAYMENT] TRIGGERED! Refunding payment for Order: {}, Customer: {}, Amount: ${}", 
                orderId, customerId, amount);

        CustomerWallet wallet = walletMap.get(customerId);
        if (wallet != null && amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newBalance = wallet.getBalance().add(amount);
            wallet.setBalance(newBalance);
            transactionHistory.remove(orderId);

            log.info("[SAGA COMPENSATION - PAYMENT] SUCCESS - Refunded ${} to Customer {}. Restored Balance: ${}", 
                    amount, customerId, newBalance);
            return new PaymentResponse(orderId, customerId, amount, true, 
                    "Refund compensation successful. Restored balance: $" + newBalance);
        }

        log.warn("[SAGA COMPENSATION - PAYMENT] IDEMPOTENT NO-OP - No wallet or amount to refund for Order: {}", orderId);
        return new PaymentResponse(orderId, customerId, BigDecimal.ZERO, true, "No payment to refund (Idempotent OK)");
    }

    public List<CustomerWallet> getAllWallets() {
        return new ArrayList<>(walletMap.values());
    }

    public CustomerWallet getWallet(String customerId) {
        return walletMap.get(customerId);
    }
}

