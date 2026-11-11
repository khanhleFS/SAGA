#!/usr/bin/env bash
echo "==================================================="
echo "  Khoi dong SAGA Microservices Demo"
echo "==================================================="

echo "[1/4] Order Service (Port 8081)..."
java -jar order-service/target/order-service-1.0.0.jar > order.log 2>&1 &
ORD_PID=$!

sleep 3

echo "[2/4] Inventory Service (Port 8082)..."
java -jar inventory-service/target/inventory-service-1.0.0.jar > inventory.log 2>&1 &
INV_PID=$!

sleep 3

echo "[3/4] Payment Service (Port 8083)..."
java -jar payment-service/target/payment-service-1.0.0.jar > payment.log 2>&1 &
PAY_PID=$!

sleep 3

echo "[4/4] Saga Orchestrator (Port 8080)..."
java -jar saga-orchestrator/target/saga-orchestrator-1.0.0.jar > orchestrator.log 2>&1 &
ORC_PID=$!

echo "==================================================="
echo "  Tat ca services dang chay ngam!"
echo "  PIDs: Order ($ORD_PID), Inventory ($INV_PID), Payment ($PAY_PID), Orchestrator ($ORC_PID)"
echo "  Web Dashboard: http://localhost:8080"
echo "  Swagger UI:    http://localhost:8080/swagger-ui.html"
echo "==================================================="
