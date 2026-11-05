package com.microservices.saga.order.service;

import com.microservices.saga.common.dto.*;
import com.microservices.saga.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SagaOrchestratorService {
    private static final Logger log = LoggerFactory.getLogger(SagaOrchestratorService.class);

    private final RestTemplate restTemplate;
    private final String inventoryUrl;
    private final String paymentUrl;

    // In-memory order repository: orderId -> Order
    private final Map<String, Order> orderRepository = new ConcurrentHashMap<>();

    public SagaOrchestratorService(
            RestTemplate restTemplate,
            @Value("${services.inventory.url}") String inventoryUrl,
            @Value("${services.payment.url}") String paymentUrl) {
        this.restTemplate = restTemplate;
        this.inventoryUrl = inventoryUrl;
        this.paymentUrl = paymentUrl;
    }

    /**
     * MAIN SAGA EXECUTION ENGINE (Orchestrator)
     */
    public synchronized OrderResponse executeOrderSaga(OrderRequest request) {
        String orderId = "ORD-" + System.currentTimeMillis();
        log.info("================================================================================");
        log.info("[SAGA ORCHESTRATOR START] Order: {}, Customer: {}, Product: {}, Qty: {}, Amount: ${}",
                orderId, request.getCustomerId(), request.getProductId(), request.getQuantity(), request.getAmount());
        log.info("================================================================================");

        Order order = new Order(orderId, request.getCustomerId(), request.getProductId(), 
                                request.getQuantity(), request.getAmount());
        order.addSagaLog(new SagaStepLog("Order Initialization", SagaStepStatus.SUCCESS, "Order created in PENDING state"));
        orderRepository.put(orderId, order);

        // -------------------------------------------------------------
        // STEP 1: Reserve Inventory (Forward Action)
        // -------------------------------------------------------------
        log.info("[SAGA STEP 1] Forward: Reserving Inventory for Order {}...", orderId);
        InventoryResponse invResponse = callReserveInventory(orderId, request.getProductId(), request.getQuantity());

        if (invResponse == null || !invResponse.isSuccess()) {
            String reason = (invResponse != null) ? invResponse.getMessage() : "Inventory Service unavailable";
            log.error("[SAGA STEP 1 FAILED] Inventory reservation failed: {}", reason);

            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason(reason);
            order.addSagaLog(new SagaStepLog("Step 1: Inventory Reservation", SagaStepStatus.FAILED, reason));
            order.addSagaLog(new SagaStepLog("SAGA Terminated", SagaStepStatus.FAILED, "Transaction halted. No compensation needed as Step 1 failed"));

            return buildResponse(order, "SAGA Failed at Step 1: " + reason);
        }

        // Step 1 Success
        order.setStatus(OrderStatus.INVENTORY_RESERVED);
        order.addSagaLog(new SagaStepLog("Step 1: Inventory Reservation", SagaStepStatus.SUCCESS, invResponse.getMessage()));
        log.info("[SAGA STEP 1 SUCCESS] Inventory reserved successfully for Order {}", orderId);

        // -------------------------------------------------------------
        // STEP 2: Process Payment (Forward Action)
        // -------------------------------------------------------------
        log.info("[SAGA STEP 2] Forward: Charging Payment for Order {}...", orderId);
        PaymentResponse payResponse = callProcessPayment(orderId, request.getCustomerId(), request.getAmount());

        if (payResponse == null || !payResponse.isSuccess()) {
            String payReason = (payResponse != null) ? payResponse.getMessage() : "Payment Service unavailable";
            log.error("[SAGA STEP 2 FAILED] Payment processing failed: {}", payReason);

            order.addSagaLog(new SagaStepLog("Step 2: Payment Debit", SagaStepStatus.FAILED, payReason));

            // =========================================================
            // COMPENSATION TRIGGER: Roll back Step 1 (Release Inventory)
            // =========================================================
            log.warn("--------------------------------------------------------------------------------");
            log.warn("[SAGA COMPENSATION TRIGGERED] Rolling back previous successful steps for Order {}", orderId);
            log.warn("--------------------------------------------------------------------------------");

            order.addSagaLog(new SagaStepLog("SAGA Compensation Started", SagaStepStatus.COMPENSATING, 
                    "Payment failed. Triggering compensating transactions in reverse order..."));

            InventoryResponse compensateInv = callCompensateInventory(orderId, request.getProductId(), request.getQuantity());
            if (compensateInv != null && compensateInv.isSuccess()) {
                log.info("[SAGA COMPENSATION STEP 1 UNDO SUCCESS] Inventory released successfully");
                order.addSagaLog(new SagaStepLog("Compensate Step 1: Release Inventory", SagaStepStatus.COMPENSATED, 
                        "Successfully rolled back inventory reservation: " + compensateInv.getMessage()));
            } else {
                log.error("[SAGA COMPENSATION STEP 1 UNDO ERROR] Failed to release inventory!");
                order.addSagaLog(new SagaStepLog("Compensate Step 1: Release Inventory", SagaStepStatus.FAILED, 
                        "CRITICAL: Failed to release inventory during compensation. Manual intervention required."));
            }

            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason("Payment Failed: " + payReason + " (Inventory was compensated & restored)");

            log.warn("[SAGA COMPLETED WITH ROLLBACK] Order {} cancelled. System state consistently restored.", orderId);
            return buildResponse(order, "SAGA Cancelled & Compensated: " + payReason);
        }

        // Step 2 Success
        order.addSagaLog(new SagaStepLog("Step 2: Payment Debit", SagaStepStatus.SUCCESS, payResponse.getMessage()));
        log.info("[SAGA STEP 2 SUCCESS] Payment processed successfully for Order {}", orderId);

        // -------------------------------------------------------------
        // STEP 3: Confirm / Finalize Transaction
        // -------------------------------------------------------------
        callConfirmInventory(orderId, request.getProductId(), request.getQuantity());

        order.setStatus(OrderStatus.COMPLETED);
        order.addSagaLog(new SagaStepLog("SAGA Completed", SagaStepStatus.SUCCESS, 
                "All distributed transactions succeeded. Order is fully confirmed."));

        log.info("================================================================================");
        log.info("[SAGA FINISHED SUCCESSFULLY] Order {} COMPLETED!", orderId);
        log.info("================================================================================");

        return buildResponse(order, "Order placed successfully! SAGA completed without rollback.");
    }

    // Helper calls to Inventory Service
    private InventoryResponse callReserveInventory(String orderId, String productId, int quantity) {
        try {
            InventoryRequest req = new InventoryRequest(orderId, productId, quantity);
            return restTemplate.postForObject(inventoryUrl + "/api/inventory/reserve", req, InventoryResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Inventory reserve: {}", e.getMessage());
            return new InventoryResponse(orderId, productId, quantity, false, "Connection error: " + e.getMessage());
        }
    }

    private InventoryResponse callCompensateInventory(String orderId, String productId, int quantity) {
        try {
            InventoryRequest req = new InventoryRequest(orderId, productId, quantity);
            return restTemplate.postForObject(inventoryUrl + "/api/inventory/compensate", req, InventoryResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Inventory compensate: {}", e.getMessage());
            return new InventoryResponse(orderId, productId, quantity, false, "Compensate connection error: " + e.getMessage());
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

    // Helper calls to Payment Service
    private PaymentResponse callProcessPayment(String orderId, String customerId, java.math.BigDecimal amount) {
        try {
            PaymentRequest req = new PaymentRequest(orderId, customerId, amount);
            return restTemplate.postForObject(paymentUrl + "/api/payment/debit", req, PaymentResponse.class);
        } catch (Exception e) {
            log.error("Exception calling Payment debit: {}", e.getMessage());
            return new PaymentResponse(orderId, customerId, amount, false, "Connection error: " + e.getMessage());
        }
    }

    private OrderResponse buildResponse(Order order, String message) {
        OrderResponse res = new OrderResponse(
                order.getOrderId(),
                order.getCustomerId(),
                order.getProductId(),
                order.getQuantity(),
                order.getAmount(),
                order.getStatus(),
                message
        );
        res.setSagaLogs(new ArrayList<>(order.getSagaLogs()));
        return res;
    }

    public List<Order> getAllOrders() {
        List<Order> list = new ArrayList<>(orderRepository.values());
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    public Order getOrder(String orderId) {
        return orderRepository.get(orderId);
    }

    public void resetAll() {
        orderRepository.clear();
        try {
            restTemplate.postForObject(inventoryUrl + "/api/inventory/reset", null, String.class);
        } catch (Exception ignored) {}
        try {
            restTemplate.postForObject(paymentUrl + "/api/payment/reset", null, String.class);
        } catch (Exception ignored) {}
        log.info("[SAGA RESET] Reset all orders, inventory, and payment accounts to default demo state.");
    }

    public Map<String, Object> getSystemSnapshot() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("orders", getAllOrders());

        try {
            Object inventoryList = restTemplate.getForObject(inventoryUrl + "/api/inventory", Object.class);
            snapshot.put("inventory", inventoryList);
        } catch (Exception e) {
            snapshot.put("inventory", Collections.emptyList());
        }

        try {
            Object paymentList = restTemplate.getForObject(paymentUrl + "/api/payment", Object.class);
            snapshot.put("wallets", paymentList);
        } catch (Exception e) {
            snapshot.put("wallets", Collections.emptyList());
        }

        return snapshot;
    }
}

