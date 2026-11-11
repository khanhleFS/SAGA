package com.microservices.saga.order.service;

import com.microservices.saga.common.dto.OrderRequest;
import com.microservices.saga.common.dto.OrderStatus;
import com.microservices.saga.order.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // In-memory order store
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    /** SAGA Step 1: Forward - tao don o trang thai PENDING. */
    public synchronized Order createOrder(OrderRequest request) {
        String orderId = "ORD-" + System.currentTimeMillis() + "-" + sequence.incrementAndGet();
        Order order = new Order(orderId, request.getCustomerId(), request.getProductId(),
                request.getQuantity(), request.getAmount());
        orders.put(orderId, order);
        log.info("[SAGA FORWARD - ORDER] Created order {} (PENDING)", orderId);
        return order;
    }

    /** SAGA Step 4: chot don khi toan bo Saga thanh cong. Idempotent. */
    public synchronized Order confirmOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) {
            return null;
        }
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.COMPLETED);
            log.info("[SAGA CONFIRM - ORDER] Order {} COMPLETED", orderId);
        }
        return order;
    }

    /** COMPENSATING TRANSACTION cua Step 1: huy don. Idempotent. */
    public synchronized Order cancelOrder(String orderId, String reason) {
        Order order = orders.get(orderId);
        if (order == null) {
            log.warn("[SAGA COMPENSATION - ORDER] IDEMPOTENT NO-OP - order {} not found", orderId);
            return null;
        }
        if (order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setFailureReason(reason);
            log.warn("[SAGA COMPENSATION - ORDER] Order {} CANCELLED: {}", orderId, reason);
        }
        return order;
    }

    public List<Order> getAllOrders() {
        List<Order> list = new ArrayList<>(orders.values());
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return list;
    }

    public Order getOrder(String orderId) {
        return orders.get(orderId);
    }

    public synchronized void reset() {
        orders.clear();
        log.info("[ORDER] Reset all orders");
    }
}
