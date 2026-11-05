@echo off
echo ===================================================
echo   Khoi dong SAGA Microservices Demo
echo ===================================================

echo [1/3] Dang khoi dong Inventory Service (Port 8082)...
start "Inventory Service :8082" java -jar inventory-service\target\inventory-service-1.0.0.jar

timeout /t 3 /nobreak >nul

echo [2/3] Dang khoi dong Payment Service (Port 8083)...
start "Payment Service :8083" java -jar payment-service\target\payment-service-1.0.0.jar

timeout /t 3 /nobreak >nul

echo [3/3] Dang khoi dong Order Orchestrator (Port 8080)...
start "Order Orchestrator :8080" java -jar order-orchestrator\target\order-orchestrator-1.0.0.jar

echo ===================================================
echo   Tat ca service dang khoi chay!
echo   Web Dashboard: http://localhost:8080
echo   Swagger UI:    http://localhost:8080/swagger-ui.html
echo ===================================================

