package com.microservices.saga.orchestrator.service;

import com.microservices.saga.common.dto.*;
import com.microservices.saga.orchestrator.model.SagaInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * SAGA ORCHESTRATOR: chi dieu phoi luong, KHONG so huu du lieu nghiep vu.
 * Ho tro ca hai che do de doi chung:
 *  - executeWithoutSaga(...): Naive flow tai hien 5 loi BUG-1..BUG-5 khi chua co SAGA.
 *  - executeOrderSaga(...):   SAGA Orchestration chuan voi 5 Golden Rules.
 */
@Service
public class SagaOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorService.class);
    private static final int DEFAULT_MAX_RETRIES = 3;

    private record Compensation(String name, BooleanSupplier action) {}

    private final RestTemplate restTemplate;
    private final String orderUrl;
    private final String inventoryUrl;
    private final String paymentUrl;
    private final int maxCompensationRetries;

    // Saga state (in-memory): orderId -> SagaInstance
    private final Map<String, SagaInstance> sagaRepository = new ConcurrentHashMap<>();

    public SagaOrchestratorService(
            RestTemplate restTemplate,
            String orderUrl,
            String inventoryUrl,
            String paymentUrl) {
        this(restTemplate, orderUrl, inventoryUrl, paymentUrl, DEFAULT_MAX_RETRIES);
    }

    @Autowired
    public SagaOrchestratorService(
            RestTemplate restTemplate,
            @Value("${services.order.url}") String orderUrl,
            @Value("${services.inventory.url}") String inventoryUrl,
            @Value("${services.payment.url}") String paymentUrl,
            @Value("${saga.compensation.max-retries:3}") int maxCompensationRetries) {
        this.restTemplate = restTemplate;
        this.orderUrl = orderUrl;
        this.inventoryUrl = inventoryUrl;
        this.paymentUrl = paymentUrl;
        this.maxCompensationRetries = Math.max(1, maxCompensationRetries);
    }

    // =========================================================================
    // PHAN 2 (AFTER SAGA): SAGA ORCHESTRATION + 5 GOLDEN RULES
    // =========================================================================

    public OrderResponse executeOrderSaga(OrderRequest request) {
        return executeOrderSaga(request, 0L, 0);
    }

    public OrderResponse executeOrderSaga(OrderRequest request, long delayBeforePaymentMs, int simulateCompensateFailures) {
        if (simulateCompensateFailures > 0) {
            armInventoryCompensateFailures(simulateCompensateFailures);
        }
        return runSagaFlow(request, delayBeforePaymentMs, false);
    }

    /**
     * Kiem chung Rule 3 (Idempotency): goi lap lai reserve & debit 2 lan voi cung 1 orderId.
     */
    public OrderResponse executeSagaDuplicateCheck(OrderRequest request) {
        return executeSagaDuplicateCheck(request, 0L);
    }

    public OrderResponse executeSagaDuplicateCheck(OrderRequest request, long delayBeforePaymentMs) {
        return runSagaFlow(request, delayBeforePaymentMs, true);
    }

    private OrderResponse runSagaFlow(OrderRequest request, long delayBeforePaymentMs, boolean simulateDuplicateCalls) {
        log.info("================================================================================");
        log.info("[SAGA START] Customer: {}, Product: {}, Qty: {}, Amount: ${}",
                request.getCustomerId(), request.getProductId(), request.getQuantity(), request.getAmount());

        Deque<Compensation> compensations = new ArrayDeque<>();

        // STEP 1: Order Service - tao don PENDING
        OrderResponse created = callCreateOrder(request);
        if (created == null || created.getOrderId() == null) {
            SagaInstance failed = newSaga("ORD-FAILED-" + System.currentTimeMillis(), request);
            failed.setStatus(OrderStatus.CANCELLED);
            failed.setFailureReason("Order Service unavailable");
            failed.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.FAILED,
                    "POST /api/order -> Connection error: Order Service unavailable"));
            return buildResponse(failed, "SAGA Failed at Step 1: Order Service unavailable");
        }

        String orderId = created.getOrderId();
        SagaInstance saga = newSaga(orderId, request);
        saga.addSagaLog(new SagaStepLog("saga-orchestrator :8080", SagaStepStatus.SUCCESS,
                "POST /api/orders -> started saga orderId=" + orderId));
        saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.SUCCESS,
                "POST /api/order -> 200 OK (orderId=" + orderId + ", status=PENDING)"));
        compensations.push(new Compensation("order-service :8081", () -> callCancelOrder(orderId, "SAGA compensation")));

        // STEP 2: Inventory Service - giu hang
        InventoryResponse inv = callReserveInventory(orderId, request.getProductId(), request.getQuantity(), false, false);
        if (inv == null || !inv.isSuccess()) {
            String reason = (inv != null) ? inv.getMessage() : "Inventory Service unavailable";
            saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.FAILED,
                    "POST /api/inventory/reserve -> FAILED (" + reason + ")"));
            return abort(saga, compensations, "Inventory failed: " + reason, maxCompensationRetries);
        }
        saga.setStatus(OrderStatus.INVENTORY_RESERVED);
        saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.SUCCESS,
                "POST /api/inventory/reserve -> 200 OK (" + inv.getMessage() + ")"));
        compensations.push(new Compensation("inventory-service :8082",
                () -> callCompensateInventory(orderId, request.getProductId(), request.getQuantity())));

        if (simulateDuplicateCalls) {
            InventoryResponse dupInv = callReserveInventory(orderId, request.getProductId(), request.getQuantity(), false, false);
            saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.SUCCESS,
                    "POST /api/inventory/reserve [retry orderId=" + orderId + "] -> " + (dupInv != null ? dupInv.getMessage() : "OK")));
        }

        sleepForDemo(delayBeforePaymentMs, saga);

        // STEP 3: Payment Service - tru tien
        PaymentResponse pay = callProcessPayment(orderId, request.getCustomerId(), request.getAmount(), false);
        if (pay == null || !pay.isSuccess()) {
            String reason = (pay != null) ? pay.getMessage() : "Payment Service unavailable";
            saga.addSagaLog(new SagaStepLog("payment-service :8083", SagaStepStatus.FAILED,
                    "POST /api/payment/debit -> FAILED (" + reason + ")"));
            return abort(saga, compensations, "Payment failed: " + reason, maxCompensationRetries);
        }
        saga.addSagaLog(new SagaStepLog("payment-service :8083", SagaStepStatus.SUCCESS,
                "POST /api/payment/debit -> 200 OK (" + pay.getMessage() + ")"));
        compensations.push(new Compensation("payment-service :8083",
                () -> callCompensatePayment(orderId, request.getCustomerId(), request.getAmount())));

        if (simulateDuplicateCalls) {
            PaymentResponse dupPay = callProcessPayment(orderId, request.getCustomerId(), request.getAmount(), false);
            saga.addSagaLog(new SagaStepLog("payment-service :8083", SagaStepStatus.SUCCESS,
                    "POST /api/payment/debit [retry orderId=" + orderId + "] -> " + (dupPay != null ? dupPay.getMessage() : "OK")));
        }

        // STEP 4: Confirm Inventory & Order
        callConfirmInventory(orderId, request.getProductId(), request.getQuantity());
        saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.SUCCESS,
                "POST /api/inventory/confirm -> 200 OK (orderId=" + orderId + ")"));

        OrderResponse confirmed = callConfirmOrder(orderId);
        if (confirmed == null || confirmed.getStatus() != OrderStatus.COMPLETED) {
            saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.FAILED,
                    "POST /api/order/" + orderId + "/confirm -> FAILED"));
            return abort(saga, compensations, "Confirm failed: Order Service could not confirm", maxCompensationRetries);
        }

        saga.setStatus(OrderStatus.COMPLETED);
        saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.SUCCESS,
                "POST /api/order/" + orderId + "/confirm -> 200 OK (status=COMPLETED)"));
        return buildResponse(saga, "Order placed successfully! SAGA completed.");
    }

    // =========================================================================
    // PHAN 1 (BEFORE SAGA): NAIVE FLOW - TAI HIEN DATA ANOMALIES & PARTIAL COMMIT
    // =========================================================================

    /**
     * Luong Naive (chua ap dung SAGA / Countermeasures):
     *  - partial_commit        (BUG-1): Payment loi nhung KHONG bu tru -> mat hang trong kho, don treo PENDING
     *  - no_semantic_lock      (BUG-2): Tru thang /deduct (reservedStock=0), gay Dirty Read
     *  - duplicate_request     (BUG-3): Gui trung reserve & debit khong co Operation Log -> tru kho/tien 2 lan
     */
    public OrderResponse executeWithoutSaga(OrderRequest request, String mode, long delayBeforePaymentMs) {
        String normalizedMode = (mode == null) ? "partial_commit" : mode.toLowerCase(Locale.ROOT);
        log.warn("[NAIVE FLOW - NO SAGA] Running mode={} for customer={}, product={}",
                normalizedMode, request.getCustomerId(), request.getProductId());

        if ("duplicate_request".equals(normalizedMode)) {
            return runNaiveDuplicate(request);
        }

        boolean combinedNaive = "naive_full_demo".equals(normalizedMode);
        boolean directDeduct = "no_semantic_lock".equals(normalizedMode) || combinedNaive;
        OrderResponse created = callCreateOrder(request);
        if (created == null || created.getOrderId() == null) {
            SagaInstance failed = newSaga("NAIVE-ERR-" + System.currentTimeMillis(), request);
            failed.setStatus(OrderStatus.CANCELLED);
            failed.setFailureReason("Order Service unavailable");
            return buildResponse(failed, "Order Service unavailable");
        }

        String orderId = created.getOrderId();
        SagaInstance saga = newSaga(orderId, request);
        saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.SUCCESS,
                "POST /api/order -> 200 OK (orderId=" + orderId + ", status=PENDING)"));

        InventoryResponse inv = callReserveInventory(orderId, request.getProductId(), request.getQuantity(), combinedNaive, directDeduct);
        String invEndpoint = directDeduct ? "POST /api/inventory/deduct" : "POST /api/inventory/reserve";
        if (inv == null || !inv.isSuccess()) {
            String reason = inv != null ? inv.getMessage() : "Inventory unavailable";
            saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.FAILED,
                    invEndpoint + " -> FAILED (" + reason + ")"));
            saga.setFailureReason(reason);
            return buildResponse(saga, saga.getFailureReason());
        }
        saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.SUCCESS,
                invEndpoint + " -> 200 OK (" + inv.getMessage() + ")"));

        if (combinedNaive) {
            InventoryResponse dupInv = callReserveInventory(orderId, request.getProductId(), request.getQuantity(), true, true);
            saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.PENDING,
                    "POST /api/inventory/deduct [retry orderId=" + orderId + "] -> 200 OK ("
                            + (dupInv != null ? dupInv.getMessage() : "OK") + ")"));
        }

        sleepForDemo(delayBeforePaymentMs, saga);

        PaymentResponse pay = callProcessPayment(orderId, request.getCustomerId(), request.getAmount(), false);
        if (pay == null || !pay.isSuccess()) {
            String reason = pay != null ? pay.getMessage() : "Payment unavailable";
            saga.addSagaLog(new SagaStepLog("payment-service :8083", SagaStepStatus.FAILED,
                    "POST /api/payment/debit -> FAILED (" + reason + ")"));
            saga.addSagaLog(new SagaStepLog("saga-orchestrator :8080", SagaStepStatus.FAILED,
                    "Flow aborted after payment failure (no compensation handler configured)"));
            saga.setFailureReason(reason);
            return buildResponse(saga, saga.getFailureReason());
        }

        callConfirmInventory(orderId, request.getProductId(), request.getQuantity());
        callConfirmOrder(orderId);
        saga.setStatus(OrderStatus.COMPLETED);
        saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.SUCCESS,
                "POST /api/order/" + orderId + "/confirm -> 200 OK (status=COMPLETED)"));
        return buildResponse(saga, "Order completed");
    }

    private OrderResponse runNaiveDuplicate(OrderRequest request) {
        OrderResponse created = callCreateOrder(request);
        String orderId = created != null ? created.getOrderId() : "NAIVE-DUP-" + System.currentTimeMillis();
        SagaInstance saga = newSaga(orderId, request);
        saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.SUCCESS,
                "POST /api/order -> 200 OK (orderId=" + orderId + ", status=PENDING)"));

        InventoryResponse inv1 = callReserveInventory(orderId, request.getProductId(), request.getQuantity(), true, false);
        saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.SUCCESS,
                "POST /api/inventory/reserve [attempt 1] -> " + (inv1 != null ? inv1.getMessage() : "OK")));
        InventoryResponse inv2 = callReserveInventory(orderId, request.getProductId(), request.getQuantity(), true, false);
        saga.addSagaLog(new SagaStepLog("inventory-service :8082", SagaStepStatus.PENDING,
                "POST /api/inventory/reserve [attempt 2, orderId=" + orderId + "] -> " + (inv2 != null ? inv2.getMessage() : "OK")));

        PaymentResponse pay1 = callProcessPayment(orderId, request.getCustomerId(), request.getAmount(), true);
        saga.addSagaLog(new SagaStepLog("payment-service :8083", SagaStepStatus.SUCCESS,
                "POST /api/payment/debit [attempt 1] -> " + (pay1 != null ? pay1.getMessage() : "OK")));
        PaymentResponse pay2 = callProcessPayment(orderId, request.getCustomerId(), request.getAmount(), true);
        saga.addSagaLog(new SagaStepLog("payment-service :8083", SagaStepStatus.PENDING,
                "POST /api/payment/debit [attempt 2, orderId=" + orderId + "] -> " + (pay2 != null ? pay2.getMessage() : "OK")));

        if (pay1 != null && pay1.isSuccess() && pay2 != null && pay2.isSuccess()) {
            callConfirmInventory(orderId, request.getProductId(), request.getQuantity());
            callConfirmOrder(orderId);
            saga.setStatus(OrderStatus.COMPLETED);
            saga.addSagaLog(new SagaStepLog("order-service :8081", SagaStepStatus.SUCCESS,
                    "POST /api/order/" + orderId + "/confirm -> 200 OK (status=COMPLETED)"));
        }
        saga.setFailureReason("Duplicate request processed twice");
        return buildResponse(saga, saga.getFailureReason());
    }

    // =========================================================================
    // COMPENSATION STACK + RETRY
    // =========================================================================

    private OrderResponse abort(SagaInstance saga, Deque<Compensation> compensations, String reason, int maxAttempts) {
        log.warn("[SAGA COMPENSATION TRIGGERED] Order {}: {} (maxAttempts={})", saga.getOrderId(), reason, maxAttempts);
        saga.addSagaLog(new SagaStepLog("saga-orchestrator :8080", SagaStepStatus.COMPENSATING,
                "Initiating reverse compensation (" + compensations.size() + " step(s), maxAttempts=" + maxAttempts + ")"));

        boolean allCompensated = true;
        while (!compensations.isEmpty()) {
            Compensation c = compensations.pop();
            boolean ok = executeWithRetry(c, maxAttempts, saga);
            if (!ok) {
                allCompensated = false;
            }
        }

        saga.setStatus(OrderStatus.CANCELLED);
        saga.setFailureReason(reason + (allCompensated ? " (all steps compensated)" : " (COMPENSATION INCOMPLETE)"));
        return buildResponse(saga, allCompensated
                ? "SAGA Cancelled & Compensated: " + reason
                : "CRITICAL: Compensation incomplete after retries: " + reason);
    }

    private boolean executeWithRetry(Compensation c, int maxAttempts, SagaInstance saga) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean ok = false;
            try {
                ok = c.action().getAsBoolean();
            } catch (Exception e) {
                log.warn("[COMPENSATION RETRY] {} attempt {}/{} exception: {}", c.name(), attempt, maxAttempts, e.getMessage());
            }

            if (ok) {
                String msg = (attempt == 1)
                        ? "Compensation 200 OK"
                        : "Compensation 200 OK (succeeded on retry attempt " + attempt + "/" + maxAttempts + ")";
                saga.addSagaLog(new SagaStepLog(c.name(), SagaStepStatus.COMPENSATED, msg));
                return true;
            }

            if (attempt < maxAttempts) {
                saga.addSagaLog(new SagaStepLog(c.name(), SagaStepStatus.COMPENSATING,
                        "Compensation attempt " + attempt + "/" + maxAttempts + " failed -> retrying..."));
            }
        }

        saga.addSagaLog(new SagaStepLog(c.name(), SagaStepStatus.FAILED,
                "CRITICAL: Compensation failed after " + maxAttempts + " attempt(s). Manual intervention required!"));
        return false;
    }

    // ------------------------------ Helpers --------------------------------

    private SagaInstance newSaga(String orderId, OrderRequest request) {
        SagaInstance saga = new SagaInstance(orderId, request.getCustomerId(), request.getProductId(),
                request.getQuantity(), request.getAmount());
        sagaRepository.put(orderId, saga);
        return saga;
    }

    private void sleepForDemo(long delayMs, SagaInstance saga) {
        if (delayMs <= 0) return;
        long clamped = Math.min(delayMs, 5000);
        saga.addSagaLog(new SagaStepLog("saga-orchestrator :8080", SagaStepStatus.PENDING,
                "Waiting " + clamped + "ms before dispatching payment-service debit..."));
        try {
            Thread.sleep(clamped);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void armInventoryCompensateFailures(int count) {
        try {
            restTemplate.postForObject(inventoryUrl + "/api/inventory/demo/compensate-failures?count=" + count, null, String.class);
        } catch (Exception e) {
            log.warn("Could not arm inventory failures: {}", e.getMessage());
        }
    }

    private OrderResponse callCreateOrder(OrderRequest request) {
        try {
            return restTemplate.postForObject(orderUrl + "/api/order", request, OrderResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Order create: {}", e.getMessage());
            return null;
        }
    }

    private OrderResponse callConfirmOrder(String orderId) {
        try {
            return restTemplate.postForObject(orderUrl + "/api/order/" + orderId + "/confirm", null, OrderResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Order confirm: {}", e.getMessage());
            return null;
        }
    }

    private boolean callCancelOrder(String orderId, String reason) {
        try {
            OrderResponse res = restTemplate.postForObject(
                    orderUrl + "/api/order/" + orderId + "/cancel?reason={reason}", null, OrderResponse.class, reason);
            return res != null && res.getStatus() == OrderStatus.CANCELLED;
        } catch (Exception e) {
            log.error("Exception calling Order cancel: {}", e.getMessage());
            return false;
        }
    }

    private InventoryResponse callReserveInventory(String orderId, String productId, int quantity,
                                                   boolean bypassIdempotency, boolean directDeduct) {
        try {
            InventoryRequest req = new InventoryRequest(orderId, productId, quantity);
            String url = directDeduct
                    ? inventoryUrl + "/api/inventory/deduct"
                    : inventoryUrl + "/api/inventory/reserve" + (bypassIdempotency ? "?bypassIdempotency=true" : "");
            return restTemplate.postForObject(url, req, InventoryResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Inventory reserve: {}", e.getMessage());
            return new InventoryResponse(orderId, productId, quantity, false, "Connection error: " + e.getMessage());
        }
    }

    private boolean callCompensateInventory(String orderId, String productId, int quantity) {
        try {
            InventoryRequest req = new InventoryRequest(orderId, productId, quantity);
            InventoryResponse res = restTemplate.postForObject(
                    inventoryUrl + "/api/inventory/compensate", req, InventoryResponse.class);
            return res != null && res.isSuccess();
        } catch (Exception e) {
            log.error("Exception calling Inventory compensate: {}", e.getMessage());
            return false;
        }
    }

    private void callConfirmInventory(String orderId, String productId, int quantity) {
        try {
            InventoryRequest req = new InventoryRequest(orderId, productId, quantity);
            restTemplate.postForObject(inventoryUrl + "/api/inventory/confirm", req, InventoryResponse.class);
        } catch (Exception e) {
            log.warn("Non-critical error confirming inventory: {}", e.getMessage());
        }
    }

    private PaymentResponse callProcessPayment(String orderId, String customerId, java.math.BigDecimal amount,
                                               boolean bypassIdempotency) {
        try {
            PaymentRequest req = new PaymentRequest(orderId, customerId, amount);
            String url = paymentUrl + "/api/payment/debit?bypassIdempotency=" + bypassIdempotency;
            return restTemplate.postForObject(url, req, PaymentResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Payment debit: {}", e.getMessage());
            return new PaymentResponse(orderId, customerId, amount, false, "Connection error: " + e.getMessage());
        }
    }

    private boolean callCompensatePayment(String orderId, String customerId, java.math.BigDecimal amount) {
        try {
            PaymentRequest req = new PaymentRequest(orderId, customerId, amount);
            PaymentResponse res = restTemplate.postForObject(paymentUrl + "/api/payment/compensate", req, PaymentResponse.class);
            return res != null && res.isSuccess();
        } catch (Exception e) {
            log.error("Exception calling Payment compensate: {}", e.getMessage());
            return false;
        }
    }

    private OrderResponse buildResponse(SagaInstance saga, String message) {
        OrderResponse res = new OrderResponse(
                saga.getOrderId(),
                saga.getCustomerId(),
                saga.getProductId(),
                saga.getQuantity(),
                saga.getAmount(),
                saga.getStatus(),
                message
        );
        res.setSagaLogs(new ArrayList<>(saga.getSagaLogs()));
        return res;
    }

    public List<SagaInstance> getAllOrders() {
        List<SagaInstance> list = new ArrayList<>(sagaRepository.values());
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    public SagaInstance getOrder(String orderId) {
        return sagaRepository.get(orderId);
    }

    public void resetAll() {
        sagaRepository.clear();
        try { restTemplate.postForObject(orderUrl + "/api/order/reset", null, String.class); } catch (Exception ignored) {}
        try { restTemplate.postForObject(inventoryUrl + "/api/inventory/reset", null, String.class); } catch (Exception ignored) {}
        try { restTemplate.postForObject(paymentUrl + "/api/payment/reset", null, String.class); } catch (Exception ignored) {}
        log.info("[SAGA RESET] Reset saga state, orders, inventory and payment to default demo state.");
    }

    public Map<String, Object> getSystemSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("orders", getAllOrders());
        try { snapshot.put("inventory", restTemplate.getForObject(inventoryUrl + "/api/inventory", Object.class)); }
        catch (Exception e) { snapshot.put("inventory", Collections.emptyList()); }
        try { snapshot.put("wallets", restTemplate.getForObject(paymentUrl + "/api/payment", Object.class)); }
        catch (Exception e) { snapshot.put("wallets", Collections.emptyList()); }
        try { snapshot.put("orderRecords", restTemplate.getForObject(orderUrl + "/api/order", Object.class)); }
        catch (Exception e) { snapshot.put("orderRecords", Collections.emptyList()); }
        return snapshot;
    }
}
