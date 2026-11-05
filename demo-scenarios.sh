#!/usr/bin/env bash
echo "======================================================================"
echo "   DEMO SAGA PATTERN - SEMINAR MICROSERVICES (15 PHÚT)"
echo "======================================================================"

echo ""
echo "[BƯỚC 0] Reset hệ thống về trạng thái ban đầu..."
curl -s -X POST http://localhost:8080/api/system/reset
echo ""

echo "----------------------------------------------------------------------"
echo "[KỊCH BẢN 1: HAPPY PATH]"
echo "Khách giàu (CUST-RICH, số dư \$5,000) đặt 1 MacBook Pro (PROD-001, \$2,000)"
echo "Kỳ vọng: Inventory TRỪ 1, Payment TRỪ \$2000, Order = COMPLETED"
echo "----------------------------------------------------------------------"
curl -s -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"customerId":"CUST-RICH","productId":"PROD-001","quantity":1,"amount":2000.00}'
echo ""
echo ""

echo "----------------------------------------------------------------------"
echo "[KỊCH BẢN 2: SAGA COMPENSATION - GIAO DỊCH BÙ TRỪ]"
echo "Khách nghèo (CUST-POOR, chỉ có \$50) đặt 1 iPhone 15 (PROD-002, \$1,000)"
echo "Kỳ vọng:"
echo "  - Bước 1: Inventory GIỮ 1 máy (Thành công)"
echo "  - Bước 2: Payment THẤT BẠI vì thiếu tiền (\$50 < \$1000)"
echo "  - TRIGGER SAGA COMPENSATION: Rollback Bước 1 (Trả lại 1 máy về kho)"
echo "  - Kết quả: Order = CANCELLED, kho và ví không bị lệch dữ liệu!"
echo "----------------------------------------------------------------------"
curl -s -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"customerId":"CUST-POOR","productId":"PROD-002","quantity":1,"amount":1000.00}'
echo ""
echo ""

echo "----------------------------------------------------------------------"
echo "[KỊCH BẢN 3: EARLY FAIL - HẾT HÀNG TẠI BƯỚC 1]"
echo "Đặt mua PS5 Pro (PROD-OUT-OF-STOCK, tồn kho = 0)"
echo "Kỳ vọng: Thất bại ngay bước 1, KHÔNG gọi sang bước 2, Order = CANCELLED"
echo "----------------------------------------------------------------------"
curl -s -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"customerId":"CUST-RICH","productId":"PROD-OUT-OF-STOCK","quantity":1,"amount":600.00}'
echo ""
echo ""

echo "======================================================================"
echo "   KIỂM TRA TRẠNG THÁI LIVE CỦA TOÀN HỆ THỐNG"
echo "======================================================================"
curl -s http://localhost:8080/api/system/snapshot
echo ""

