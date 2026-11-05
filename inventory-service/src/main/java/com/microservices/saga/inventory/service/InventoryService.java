package com.microservices.saga.inventory.service;

import com.microservices.saga.common.dto.InventoryRequest;
import com.microservices.saga.common.dto.InventoryResponse;
import com.microservices.saga.inventory.model.ProductInventory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryService {
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    // In-memory stock store (thread-safe)
    private final Map<String, ProductInventory> inventoryMap = new ConcurrentHashMap<>();

    // Map to keep track of active reservations by orderId: orderId -> (productId -> quantity)
    private final Map<String, Map<String, Integer>> orderReservations = new ConcurrentHashMap<>();

    public InventoryService() {
        initData();
    }

    public synchronized void initData() {
        inventoryMap.clear();
        orderReservations.clear();
        // Seed default products
        inventoryMap.put("PROD-001", new ProductInventory("PROD-001", "MacBook Pro M3", 10, 0));
        inventoryMap.put("PROD-002", new ProductInventory("PROD-002", "iPhone 15 Pro", 5, 0));
        inventoryMap.put("PROD-OUT-OF-STOCK", new ProductInventory("PROD-OUT-OF-STOCK", "PlayStation 5 Pro (Out of stock demo)", 0, 0));
        log.info("[INVENTORY] Initial stock seeded: {}", inventoryMap.keySet());
    }

    /**
     * SAGA Step 1: Forward Action - Reserve product stock
     */
    public synchronized InventoryResponse reserveStock(InventoryRequest request) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();
        int quantity = request.getQuantity();

        log.info("[SAGA FORWARD - INVENTORY] Request received for Order: {}, Product: {}, Quantity: {}", 
                orderId, productId, quantity);

        ProductInventory item = inventoryMap.get(productId);
        if (item == null) {
            log.warn("[SAGA FORWARD - INVENTORY] FAILED - Product {} not found", productId);
            return new InventoryResponse(orderId, productId, quantity, false, "Product not found: " + productId);
        }

        if (item.getAvailableStock() < quantity) {
            log.warn("[SAGA FORWARD - INVENTORY] FAILED - Product {} out of stock. Required: {}, Available: {}", 
                    productId, quantity, item.getAvailableStock());
            return new InventoryResponse(orderId, productId, quantity, false, 
                    "Out of stock. Available: " + item.getAvailableStock() + ", Requested: " + quantity);
        }

        // Reserve stock
        item.setAvailableStock(item.getAvailableStock() - quantity);
        item.setReservedStock(item.getReservedStock() + quantity);

        // Record reservation for potential compensation
        orderReservations.computeIfAbsent(orderId, k -> new HashMap<>()).put(productId, quantity);

        log.info("[SAGA FORWARD - INVENTORY] SUCCESS - Reserved {} of {}. Remaining available: {}, Reserved: {}", 
                quantity, productId, item.getAvailableStock(), item.getReservedStock());

        return new InventoryResponse(orderId, productId, quantity, true, "Inventory reserved successfully");
    }

    /**
     * SAGA Step 1 Undo: COMPENSATING TRANSACTION - Release reserved stock back to available
     */
    public synchronized InventoryResponse compensateReleaseStock(InventoryRequest request) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();

        log.warn("[SAGA COMPENSATION - INVENTORY] TRIGGERED! Undoing stock reservation for Order: {}, Product: {}", 
                orderId, productId);

        Map<String, Integer> reservations = orderReservations.get(orderId);
        int quantityToRelease = request.getQuantity();

        if (reservations != null && reservations.containsKey(productId)) {
            quantityToRelease = reservations.remove(productId);
            if (reservations.isEmpty()) {
                orderReservations.remove(orderId);
            }
        }

        ProductInventory item = inventoryMap.get(productId);
        if (item != null && quantityToRelease > 0) {
            item.setReservedStock(Math.max(0, item.getReservedStock() - quantityToRelease));
            item.setAvailableStock(item.getAvailableStock() + quantityToRelease);
            log.info("[SAGA COMPENSATION - INVENTORY] SUCCESS - Released {} items for {}. Restored Available: {}, Reserved: {}", 
                    quantityToRelease, productId, item.getAvailableStock(), item.getReservedStock());
            return new InventoryResponse(orderId, productId, quantityToRelease, true, 
                    "Compensation successful: " + quantityToRelease + " items released back to stock");
        }

        log.warn("[SAGA COMPENSATION - INVENTORY] IDEMPOTENT NO-OP - No active reservation found for Order: {}", orderId);
        return new InventoryResponse(orderId, productId, 0, true, "No reserved items to release (Idempotent OK)");
    }

    /**
     * Finalize deduction when entire Saga succeeds
     */
    public synchronized InventoryResponse confirmDeduction(InventoryRequest request) {
        String orderId = request.getOrderId();
        String productId = request.getProductId();

        Map<String, Integer> reservations = orderReservations.remove(orderId);
        int qty = (reservations != null && reservations.containsKey(productId)) 
                ? reservations.get(productId) 
                : request.getQuantity();

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

