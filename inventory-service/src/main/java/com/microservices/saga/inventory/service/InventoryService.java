package com.microservices.saga.inventory.service;

import com.microservices.saga.common.dto.InventoryRequest;
import com.microservices.saga.common.dto.InventoryResponse;
import com.microservices.saga.inventory.model.ProductInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private enum ReservationState {
        RESERVED,
        CONFIRMED,
        COMPENSATED
    }

    // In-memory stock store (mo phong Local DB cua Inventory Service)
    private final Map<String, ProductInventory> inventoryMap = new ConcurrentHashMap<>();

    // Track active reservations: orderId -> (productId -> quantity)
    private final Map<String, Map<String, Integer>> orderReservations = new ConcurrentHashMap<>();

    // Idempotency store: orderId -> trang thai da xu ly
    private final Map<String, ReservationState> reservationStates = new ConcurrentHashMap<>();

    // Dung de mo phong loi mang tam thoi trong demo/test Rule 4 (Compensation Retry)
    private final AtomicInteger remainingSimulatedCompensateFailures = new AtomicInteger(0);

    public InventoryService() {
        initData();
    }

    public synchronized void initData() {
        inventoryMap.clear();
        orderReservations.clear();
        reservationStates.clear();
        remainingSimulatedCompensateFailures.set(0);

        inventoryMap.put("PROD-001", new ProductInventory("PROD-001", "MacBook Pro M3", 10, 0, new java.math.BigDecimal("2000.00")));
        inventoryMap.put("PROD-002", new ProductInventory("PROD-002", "iPhone 15 Pro", 5, 0, new java.math.BigDecimal("1000.00")));
        inventoryMap.put("PROD-OUT-OF-STOCK", new ProductInventory("PROD-OUT-OF-STOCK", "PlayStation 5 Pro (Out of stock demo)", 0, 0, new java.math.BigDecimal("700.00")));
        log.info("[INVENTORY] Initial stock seeded: {}", inventoryMap.keySet());
    }

    /**
     * Cap nhat gia san pham (dung de mo phong giao dich khac sua gia chen ngang -> gay Fuzzy Read).
     */
    public synchronized InventoryResponse updateProductPrice(String productId, java.math.BigDecimal newPrice) {
        ProductInventory item = inventoryMap.get(productId);
        if (item == null) {
            return new InventoryResponse(null, productId, 0, false, "Product not found: " + productId);
        }
        java.math.BigDecimal oldPrice = item.getPrice();
        item.setPrice(newPrice);
        log.warn("[INVENTORY PRICE UPDATE] Product {} price changed from ${} to ${}", productId, oldPrice, newPrice);
        return new InventoryResponse(null, productId, item.getAvailableStock(), true,
                "Price updated from $" + oldPrice + " to $" + newPrice, newPrice);
    }

    public void simulateCompensateFailures(int failureCount) {
        remainingSimulatedCompensateFailures.set(Math.max(0, failureCount));
        log.warn("[INVENTORY] Armed {} simulated compensation failure(s) for demo/testing", failureCount);
    }

    /**
     * SAGA Step 2: Forward Action - Reserve product stock (Idempotent theo orderId).
     */
    public synchronized InventoryResponse reserveStock(InventoryRequest request) {
        return reserveStock(request, false);
    }

    public synchronized InventoryResponse reserveStock(InventoryRequest request, boolean bypassIdempotency) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();
        int quantity = request.getQuantity();

        log.info("[SAGA FORWARD - INVENTORY] Request for Order: {}, Product: {}, Qty: {}", orderId, productId, quantity);

        if (!bypassIdempotency && orderId != null && reservationStates.containsKey(orderId)) {
            ReservationState state = reservationStates.get(orderId);
            log.info("[INVENTORY IDEMPOTENT] Order {} already processed (state={}). Skipping duplicate deduction.", orderId, state);
            return new InventoryResponse(orderId, productId, quantity, true,
                    "Idempotent OK: Stock already processed for order " + orderId + " (" + state + ")");
        }

        ProductInventory item = inventoryMap.get(productId);
        if (item == null) {
            return new InventoryResponse(orderId, productId, quantity, false, "Product not found: " + productId);
        }

        if (item.getAvailableStock() < quantity) {
            log.warn("[SAGA FORWARD - INVENTORY] FAILED - Product {} out of stock. Required: {}, Available: {}",
                    productId, quantity, item.getAvailableStock());
            return new InventoryResponse(orderId, productId, quantity, false,
                    "Out of stock. Available: " + item.getAvailableStock() + ", Requested: " + quantity);
        }

        // Semantic Lock: chuyen tu availableStock sang reservedStock
        item.setAvailableStock(item.getAvailableStock() - quantity);
        item.setReservedStock(item.getReservedStock() + quantity);

        if (orderId != null) {
            orderReservations.computeIfAbsent(orderId, k -> new HashMap<>()).merge(productId, quantity, Integer::sum);
            reservationStates.put(orderId, ReservationState.RESERVED);
        }

        log.info("[SAGA FORWARD - INVENTORY] SUCCESS - Reserved {} of {}. Available: {}, Reserved: {}",
                quantity, productId, item.getAvailableStock(), item.getReservedStock());

        return new InventoryResponse(orderId, productId, quantity, true, "Inventory reserved successfully", item.getPrice());
    }

    /**
     * Naive Flow (BUG-2 demo): Tru thang vao availableStock ma khong dung Semantic Lock (reservedStock = 0).
     */
    public synchronized InventoryResponse deductDirectWithoutLock(InventoryRequest request) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();
        int quantity = request.getQuantity();

        ProductInventory item = inventoryMap.get(productId);
        if (item == null) {
            return new InventoryResponse(orderId, productId, quantity, false, "Product not found: " + productId);
        }
        if (item.getAvailableStock() < quantity) {
            return new InventoryResponse(orderId, productId, quantity, false,
                    "Out of stock. Available: " + item.getAvailableStock() + ", Requested: " + quantity);
        }

        // Tru dut ngay lap tuc, khong co reservedStock (vi pham Rule 2 - Semantic Lock)
        item.setAvailableStock(item.getAvailableStock() - quantity);
        log.info("[INVENTORY DEDUCT] Deducted {} of {}. Remaining available: {}",
                quantity, productId, item.getAvailableStock());
        return new InventoryResponse(orderId, productId, quantity, true,
                "Stock deducted (product=" + productId + ", available=" + item.getAvailableStock() + ")");
    }

    /**
     * SAGA Step 2 Undo: COMPENSATING TRANSACTION - Release reserved stock back to available (Idempotent).
     */
    public synchronized InventoryResponse compensateReleaseStock(InventoryRequest request) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();

        if (remainingSimulatedCompensateFailures.get() > 0) {
            int remaining = remainingSimulatedCompensateFailures.decrementAndGet();
            log.error("[SAGA COMPENSATION - INVENTORY] Simulated transient failure! Remaining simulated failures: {}", remaining);
            return new InventoryResponse(orderId, productId, 0, false,
                    "Simulated network failure releasing inventory (remaining failures: " + remaining + ")");
        }

        log.warn("[SAGA COMPENSATION - INVENTORY] TRIGGERED for Order: {}, Product: {}", orderId, productId);

        if (orderId != null && reservationStates.get(orderId) == ReservationState.COMPENSATED) {
            log.info("[INVENTORY IDEMPOTENT] Order {} already compensated. No-op.", orderId);
            return new InventoryResponse(orderId, productId, 0, true, "Already compensated (Idempotent OK)");
        }

        Map<String, Integer> reservations = (orderId != null) ? orderReservations.get(orderId) : null;
        if (reservations == null || !reservations.containsKey(productId)) {
            log.warn("[SAGA COMPENSATION - INVENTORY] IDEMPOTENT NO-OP - No active reservation for Order: {}", orderId);
            return new InventoryResponse(orderId, productId, 0, true, "No reserved items to release (Idempotent OK)");
        }

        int quantityToRelease = reservations.remove(productId);
        if (reservations.isEmpty()) {
            orderReservations.remove(orderId);
        }
        reservationStates.put(orderId, ReservationState.COMPENSATED);

        ProductInventory item = inventoryMap.get(productId);
        if (item != null && quantityToRelease > 0) {
            item.setReservedStock(Math.max(0, item.getReservedStock() - quantityToRelease));
            item.setAvailableStock(item.getAvailableStock() + quantityToRelease);
            log.info("[SAGA COMPENSATION - INVENTORY] SUCCESS - Released {} items of {}. Available: {}, Reserved: {}",
                    quantityToRelease, productId, item.getAvailableStock(), item.getReservedStock());
            return new InventoryResponse(orderId, productId, quantityToRelease, true,
                    "Compensation successful: " + quantityToRelease + " items released back to stock");
        }

        return new InventoryResponse(orderId, productId, 0, true, "No reserved items to release (Idempotent OK)");
    }

    /**
     * Finalize deduction when entire Saga succeeds (Idempotent theo orderId).
     */
    public synchronized InventoryResponse confirmDeduction(InventoryRequest request) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();

        if (orderId != null && reservationStates.get(orderId) == ReservationState.CONFIRMED) {
            return new InventoryResponse(orderId, productId, 0, true, "Already confirmed (Idempotent OK)");
        }

        Map<String, Integer> reservations = (orderId != null) ? orderReservations.remove(orderId) : null;
        if (reservations == null || !reservations.containsKey(productId)) {
            return new InventoryResponse(orderId, productId, 0, true, "No active reservation to confirm (Idempotent OK)");
        }

        int qty = reservations.get(productId);
        reservationStates.put(orderId, ReservationState.CONFIRMED);

        ProductInventory item = inventoryMap.get(productId);
        if (item != null) {
            item.setReservedStock(Math.max(0, item.getReservedStock() - qty));
            log.info("[INVENTORY CONFIRM] Order {} completed. Permanently deducted {} of {}", orderId, qty, productId);
        }

        return new InventoryResponse(orderId, productId, qty, true, "Stock deduction confirmed permanently");
    }

    public List<ProductInventory> getAllInventory() {
        return new ArrayList<>(inventoryMap.values());
    }

    public ProductInventory getProduct(String productId) {
        return inventoryMap.get(productId);
    }
}
