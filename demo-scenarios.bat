@echo off
chcp 65001 >nul
echo ======================================================================
echo    DEMO SAGA PATTERN - SEMINAR MICROSERVICES (15 PHUT)
echo ======================================================================

echo.
echo [BUOC 0] Reset he thong ve trang thai ban dau...
curl -s -X POST http://localhost:8080/api/system/reset
echo.
echo.

echo ----------------------------------------------------------------------
echo [KICH BAN 1: HAPPY PATH]
echo Khach giau (CUST-RICH, so du $5,000) dat 1 MacBook Pro (PROD-001, $2,000)
echo Ky vong: Inventory TRU 1, Payment TRU $2000, Order = COMPLETED
echo ----------------------------------------------------------------------
curl -s -X POST http://localhost:8080/api/orders ^
     -H "Content-Type: application/json" ^
     -d "{\"customerId\":\"CUST-RICH\",\"productId\":\"PROD-001\",\"quantity\":1,\"amount\":2000.00}"
echo.
echo.

pause

echo ----------------------------------------------------------------------
echo [KICH BAN 2: SAGA COMPENSATION - GIAO DICH BU TRU]
echo Khach ngheo (CUST-POOR, chi co $50) dat 1 iPhone 15 (PROD-002, $1,000)
echo Ky vong:
echo   - Buoc 1: Inventory GIU 1 may (Thanh cong)
echo   - Buoc 2: Payment THAT BAI vi thieu tien ($50 ^< $1000)
echo   - TRIGGER SAGA COMPENSATION: Rollback Buoc 1 (Tra lai 1 may ve kho)
echo   - Ket qua: Order = CANCELLED, kho va vi khong bi lech du lieu!
echo ----------------------------------------------------------------------
curl -s -X POST http://localhost:8080/api/orders ^
     -H "Content-Type: application/json" ^
     -d "{\"customerId\":\"CUST-POOR\",\"productId\":\"PROD-002\",\"quantity\":1,\"amount\":1000.00}"
echo.
echo.

pause

echo ----------------------------------------------------------------------
echo [KICH BAN 3: EARLY FAIL - HET HANG TAI BUOC 1]
echo Dat mua PS5 Pro (PROD-OUT-OF-STOCK, ton kho = 0)
echo Ky vong: That bai ngay buoc 1, KHONG goi sang buoc 2, Order = CANCELLED
echo ----------------------------------------------------------------------
curl -s -X POST http://localhost:8080/api/orders ^
     -H "Content-Type: application/json" ^
     -d "{\"customerId\":\"CUST-RICH\",\"productId\":\"PROD-OUT-OF-STOCK\",\"quantity\":1,\"amount\":600.00}"
echo.
echo.

echo ======================================================================
echo    KIEM TRA TRANG THAI LIVE CUA TOAN HE THONG
echo ======================================================================
curl -s http://localhost:8080/api/system/snapshot
echo.
pause

