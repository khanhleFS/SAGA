@echo off
echo ===================================================
echo   Khoi dong SAGA Microservices Demo
echo ===================================================

echo [1/4] Order Service (Port 8081)...
start "Order Service :8081" java -jar order-service\target\order-service-1.0.0.jar

timeout /t 3 /nobreak >nul

echo [2/4] Inventory Service (Port 8082)...
start "Inventory Service :8082" java -jar inventory-service\target\inventory-service-1.0.0.jar

timeout /t 3 /nobreak >nul

echo [3/4] Payment Service (Port 8083)...
start "Payment Service :8083" java -jar payment-service\target\payment-service-1.0.0.jar

timeout /t 3 /nobreak >nul

echo [4/4] Saga Orchestrator (Port 8080)...
start "Saga Orchestrator :8080" java -jar saga-orchestrator\target\saga-orchestrator-1.0.0.jar

echo ===================================================
echo   Tat ca service dang khoi chay!
echo   Web Dashboard: http://localhost:8080
echo   Swagger UI:    http://localhost:8080/swagger-ui.html
echo ===================================================
