package com.microservices.saga.order.controller;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderResponse;
import com.microservices.saga.order.model.Order;
import com.microservices.saga.order.service.SagaOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Order Orchestrator SAGA API", description = "Endpoints to create orders and orchestrate distributed transactions")
@CrossOrigin(origins = "*")
public class OrderController {

    private final SagaOrchestratorService sagaService;

    public OrderController(SagaOrchestratorService sagaService) {
        this.sagaService = sagaService;
    }

    @PostMapping("/orders")
    @Operation(summary = "Submit new order and initiate SAGA transaction")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        OrderResponse response = sagaService.executeOrderSaga(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/orders")
    @Operation(summary = "Get list of all orders with their SAGA execution logs")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(sagaService.getAllOrders());
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get detailed order status and SAGA trace logs")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        Order order = sagaService.getOrder(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
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

