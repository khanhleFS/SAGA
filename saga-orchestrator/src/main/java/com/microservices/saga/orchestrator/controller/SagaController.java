package com.microservices.saga.orchestrator.controller;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderResponse;
import com.microservices.saga.orchestrator.model.SagaInstance;
import com.microservices.saga.orchestrator.service.SagaOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Saga Orchestrator API", description = "Endpoints to run SAGA vs Naive flows and inspect system state")
@CrossOrigin(origins = "*")
public class SagaController {

    private final SagaOrchestratorService sagaService;

    public SagaController(SagaOrchestratorService sagaService) {
        this.sagaService = sagaService;
    }

    @PostMapping("/orders")
    @Operation(summary = "Run SAGA transaction (With 5 Golden Rules)")
    public ResponseEntity<OrderResponse> createOrder(
            @RequestBody OrderRequest request,
            @RequestParam(defaultValue = "0") long delayBeforePaymentMs,
            @RequestParam(defaultValue = "0") int simulateCompensateFailures) {
        return ResponseEntity.ok(sagaService.executeOrderSaga(request, delayBeforePaymentMs, simulateCompensateFailures));
    }

    @PostMapping("/orders/duplicate-check")
    @Operation(summary = "Run SAGA with duplicate reserve & debit calls to prove Rule 3 Idempotency")
    public ResponseEntity<OrderResponse> createOrderDuplicateCheck(
            @RequestBody OrderRequest request,
            @RequestParam(defaultValue = "0") long delayBeforePaymentMs) {
        return ResponseEntity.ok(sagaService.executeSagaDuplicateCheck(request, delayBeforePaymentMs));
    }

    @PostMapping("/orders/without-saga")
    @Operation(summary = "Run Naive flow WITHOUT SAGA to reproduce BUG-1..BUG-5")
    public ResponseEntity<OrderResponse> createOrderWithoutSaga(
            @RequestBody OrderRequest request,
            @RequestParam(defaultValue = "partial_commit") String mode,
            @RequestParam(defaultValue = "0") long delayBeforePaymentMs) {
        return ResponseEntity.ok(sagaService.executeWithoutSaga(request, mode, delayBeforePaymentMs));
    }

    @GetMapping("/orders")
    @Operation(summary = "Get list of all SAGA executions and their trace logs")
    public ResponseEntity<List<SagaInstance>> getAllOrders() {
        return ResponseEntity.ok(sagaService.getAllOrders());
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get detailed SAGA trace logs by orderId")
    public ResponseEntity<SagaInstance> getOrder(@PathVariable String orderId) {
        SagaInstance order = sagaService.getOrder(orderId);
        return order == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(order);
    }

    @GetMapping("/system/snapshot")
    @Operation(summary = "Get unified live state of Orders, Inventory, and Payment balances")
    public ResponseEntity<Map<String, Object>> getSystemSnapshot() {
        return ResponseEntity.ok(sagaService.getSystemSnapshot());
    }

    @PostMapping("/system/reset")
    @Operation(summary = "Reset entire system to initial demo state")
    public ResponseEntity<String> resetSystem() {
        sagaService.resetAll();
        return ResponseEntity.ok("All systems reset successfully for next demo iteration");
    }
}
