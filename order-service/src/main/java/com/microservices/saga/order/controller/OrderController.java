package com.microservices.saga.order.controller;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderResponse;
import com.microservices.saga.order.model.Order;
import com.microservices.saga.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order")
@Tag(name = "Order Service API", description = "Order data + SAGA forward/compensation endpoints")
@CrossOrigin(origins = "*")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "SAGA forward: create order in PENDING state")
    public ResponseEntity<OrderResponse> create(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(toResponse(orderService.createOrder(request), "Order created (PENDING)"));
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "SAGA confirm: mark order COMPLETED")
    public ResponseEntity<OrderResponse> confirm(@PathVariable String orderId) {
        Order order = orderService.confirmOrder(orderId);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(order, "Order confirmed"));
    }

    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "SAGA compensation: cancel order")
    public ResponseEntity<OrderResponse> cancel(@PathVariable String orderId,
                                                @RequestParam(defaultValue = "Cancelled") String reason) {
        Order order = orderService.cancelOrder(orderId, reason);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(order, "Order cancelled"));
    }

    @GetMapping
    @Operation(summary = "List all orders")
    public ResponseEntity<List<Order>> getAll() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by id")
    public ResponseEntity<Order> get(@PathVariable String orderId) {
        Order order = orderService.getOrder(orderId);
        return order == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(order);
    }

    @PostMapping("/reset")
    @Operation(summary = "Reset all orders (demo)")
    public ResponseEntity<String> reset() {
        orderService.reset();
        return ResponseEntity.ok("Order data reset");
    }

    private OrderResponse toResponse(Order o, String message) {
        return new OrderResponse(o.getOrderId(), o.getCustomerId(), o.getProductId(),
                o.getQuantity(), o.getAmount(), o.getStatus(), message);
    }
}
