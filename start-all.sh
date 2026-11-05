#!/usr/bin/env bash
echo "==================================================="
echo "  Khởi động SAGA Microservices Demo"
echo "==================================================="

echo "[1/3] Khởi động Inventory Service (Port 8082)..."
java -jar inventory-service/target/inventory-service-1.0.0.jar > inventory.log 2>&1 &
INV_PID=$!

sleep 3

echo "[2/3] Khởi động Payment Service (Port 8083)..."
java -jar payment-service/target/payment-service-1.0.0.jar > payment.log 2>&1 &
PAY_PID=$!

sleep 3

echo "[3/3] Khởi động Order Orchestrator (Port 8080)..."
java -jar order-orchestrator/target/order-orchestrator-1.0.0.jar > order.log 2>&1 &
ORD_PID=$!

echo "==================================================="
echo "  Tất cả services đang chạy ngầm!"
echo "  PIDs: Inventory ($INV_PID), Payment ($PAY_PID), Order ($ORD_PID)"
echo "  Web Dashboard: http://localhost:8080"
echo "  Swagger UI:    http://localhost:8080/swagger-ui.html"
echo "==================================================="

