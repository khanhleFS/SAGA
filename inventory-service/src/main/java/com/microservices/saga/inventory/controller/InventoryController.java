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
    public ResponseEntity<InventoryResponse> reserveStock(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.reserveStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/compensate")
    @Operation(summary = "SAGA Compensating Step: Release Reserved Stock")
    public ResponseEntity<InventoryResponse> compensateRelease(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.compensateReleaseStock(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    @Operation(summary = "SAGA Finalization: Confirm stock deduction")
    public ResponseEntity<InventoryResponse> confirmDeduction(@RequestBody InventoryRequest request) {
        InventoryResponse response = inventoryService.confirmDeduction(request);
        return ResponseEntity.ok(response);
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
        if (item == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(item);
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset inventory to default values for demo")
    public ResponseEntity<String> reset() {
        inventoryService.initData();
        return ResponseEntity.ok("Inventory reset to initial demo state");
    }
}

