package com.microservices.saga.inventory.controller;

import com.microservices.saga.common.dto.InventoryRequest;
import com.microservices.saga.common.dto.InventoryResponse;
import com.microservices.saga.inventory.model.ProductInventory;
import com.microservices.saga.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory SAGA API", description = "Endpoints for stock reservation, confirmation and compensation")
@CrossOrigin(origins = "*")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/reserve")
    @Operation(summary = "SAGA Forward Step: Reserve Stock")
    public ResponseEntity<InventoryResponse> reserveStock(
            @RequestBody InventoryRequest request,
            @RequestParam(defaultValue = "false") boolean bypassIdempotency) {
        return ResponseEntity.ok(inventoryService.reserveStock(request, bypassIdempotency));
    }

    @PostMapping("/deduct")
    @Operation(summary = "Naive Step (No Semantic Lock): Deduct stock directly")
    public ResponseEntity<InventoryResponse> deductDirect(@RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.deductDirectWithoutLock(request));
    }

    @PostMapping("/compensate")
    @Operation(summary = "SAGA Compensating Step: Release Reserved Stock")
    public ResponseEntity<InventoryResponse> compensateRelease(@RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.compensateReleaseStock(request));
    }

    @PostMapping("/confirm")
    @Operation(summary = "SAGA Finalization: Confirm stock deduction")
    public ResponseEntity<InventoryResponse> confirmDeduction(@RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.confirmDeduction(request));
    }

    @PostMapping("/demo/compensate-failures")
    @Operation(summary = "Demo helper: simulate N transient failures during compensation")
    public ResponseEntity<String> simulateCompensateFailures(@RequestParam(defaultValue = "2") int count) {
        inventoryService.simulateCompensateFailures(count);
        return ResponseEntity.ok("Armed " + count + " simulated compensation failures");
    }

    @GetMapping
    @Operation(summary = "Get list of all products in inventory")
    public ResponseEntity<List<ProductInventory>> getAll() {
        return ResponseEntity.ok(inventoryService.getAllInventory());
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory details for a product")
    public ResponseEntity<ProductInventory> getProduct(@PathVariable String productId) {
        ProductInventory item = inventoryService.getProduct(productId);
        return item == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(item);
    }

    @PostMapping("/{productId}/price")
    @Operation(summary = "Demo helper: update product unit price mid-flight to trigger or test Fuzzy Read")
    public ResponseEntity<InventoryResponse> updatePrice(
            @PathVariable String productId,
            @RequestParam java.math.BigDecimal newPrice) {
        return ResponseEntity.ok(inventoryService.updateProductPrice(productId, newPrice));
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset inventory to default values for demo")
    public ResponseEntity<String> reset() {
        inventoryService.initData();
        return ResponseEntity.ok("Inventory reset to initial demo state");
    }
}
