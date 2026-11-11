package com.microservices.saga.inventory;

import com.microservices.saga.common.dto.InventoryRequest;
import com.microservices.saga.common.dto.InventoryResponse;
import com.microservices.saga.inventory.model.ProductInventory;
import com.microservices.saga.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BUOC VA 3 (Rule 3): Va BUG-3 bang Idempotency Key (orderId) cho Reserve, Confirm va Compensate.
 */
class Rule3_InventoryIdempotencyTest {

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService();
    }

    @Test
    @DisplayName("BUG-3 (Without Idempotency): Duplicate reserve calls deduct stock twice")
    void bug3_withoutIdempotencyDeductsTwice() {
        InventoryRequest req = new InventoryRequest("ORD-DUP", "PROD-002", 1);

        inventoryService.reserveStock(req, true);
        inventoryService.reserveStock(req, true);

        ProductInventory prod = inventoryService.getProduct("PROD-002");
        assertEquals(3, prod.getAvailableStock(), "BUG-3: Duplicate call deducted 2 units (5 -> 3)");
        assertEquals(2, prod.getReservedStock());
    }

    @Test
    @DisplayName("Rule 3 (With Idempotency): Calling reserve 3 times for same orderId reserves only once")
    void rule3_reserveIsIdempotent() {
        InventoryRequest req = new InventoryRequest("ORD-IDEMP-1", "PROD-002", 1);

        InventoryResponse r1 = inventoryService.reserveStock(req);
        InventoryResponse r2 = inventoryService.reserveStock(req);
        InventoryResponse r3 = inventoryService.reserveStock(req);

        assertTrue(r1.isSuccess() && r2.isSuccess() && r3.isSuccess());
        ProductInventory prod = inventoryService.getProduct("PROD-002");
        assertEquals(4, prod.getAvailableStock(), "Available stock must decrease only once (5 -> 4)");
        assertEquals(1, prod.getReservedStock());
    }

    @Test
    @DisplayName("Rule 3: Calling confirm or compensate multiple times does not double-apply")
    void rule3_confirmAndCompensateAreIdempotent() {
        InventoryRequest req = new InventoryRequest("ORD-IDEMP-2", "PROD-002", 1);
        inventoryService.reserveStock(req);

        inventoryService.compensateReleaseStock(req);
        inventoryService.compensateReleaseStock(req); // duplicate call

        ProductInventory prod = inventoryService.getProduct("PROD-002");
        assertEquals(5, prod.getAvailableStock(), "Compensation called twice must restore only 1 unit");
        assertEquals(0, prod.getReservedStock());

        // Confirm duplicate check
        InventoryRequest req2 = new InventoryRequest("ORD-IDEMP-3", "PROD-002", 1);
        inventoryService.reserveStock(req2);
        inventoryService.confirmDeduction(req2);
        inventoryService.confirmDeduction(req2);

        assertEquals(4, inventoryService.getProduct("PROD-002").getAvailableStock());
        assertEquals(0, inventoryService.getProduct("PROD-002").getReservedStock());
    }
}
