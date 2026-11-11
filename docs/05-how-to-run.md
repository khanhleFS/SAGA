# 5. How to run

## Kiến trúc demo
Orchestrator đứng **ngoài** 3 service nghiệp vụ. Nó chỉ điều phối luồng, còn mỗi service tự giữ dữ liệu độc lập (dùng In-Memory Store — không cần cài thêm Database để chạy nhẹ và reset đồng bộ nhanh khi demo).

```
          Dashboard (http://localhost:8080)
                       │ REST
                       ▼
             SAGA ORCHESTRATOR (:8080)
        Forward: T1 → T2 → T3 → T4
        Lỗi: chạy compensation ngược lại
         │             │              │
         ▼             ▼              ▼
   ORDER (:8081)  INVENTORY (:8082)  PAYMENT (:8083)
```

| Bước | Forward (service) | Compensation |
|---|---|---|
| T1 | Order: tạo đơn `PENDING` | Order: hủy đơn (`CANCELLED`) |
| T2 | Inventory: giữ hàng | Inventory: nhả hàng |
| T3 | Payment: trừ tiền | Payment: hoàn tiền |
| T4 | Confirm Inventory và Order: `COMPLETED` | (không cần) |

Orchestrator đẩy compensation vào một stack sau mỗi bước thành công. Khi có lỗi, nó chạy ngược lại theo thứ tự LIFO.

**Dữ liệu mẫu**
| Loại | ID | Ghi chú |
|---|---|---|
| Sản phẩm | `PROD-001` | MacBook Pro, tồn 10, giá \$2,000 |
| Sản phẩm | `PROD-002` | iPhone 15 Pro, tồn 5, giá \$1,000 |
| Sản phẩm | `PROD-OUT-OF-STOCK` | PS5 Pro, tồn 0 |
| Ví | `CUST-RICH` | \$5,000 |
| Ví | `CUST-POOR` | \$50 |
| Ví | `CUST-NORMAL` | \$1,200 |

## Yêu cầu
JDK 21, Maven 3.8+ (hoặc Docker và Docker Compose).

## Cách 1: Docker Compose
```bash
docker-compose up --build
```

## Cách 2: Maven
```bash
mvn clean package -DskipTests
start-all.bat        # Windows
./start-all.sh       # Linux/macOS
```

## Chạy bộ Test Đối Chứng (Dò lỗi $\rightarrow$ Vá từng bước)
```bash
# Chạy toàn bộ test across 4 modules
mvn test

# Chạy từng bài test minh họa khi thuyết trình:
mvn test -Dtest=WithoutSagaProblemsTest            # Phần 1: Tái hiện Partial Commit & Dirty Read
mvn test -Dtest=Rule1_AtomicityAndCompensationTest # Case 1: LIFO Compensation + Commutative Updates
mvn test -Dtest=Rule2_SemanticLockIsolationTest    # Case 2: Semantic Lock (PENDING/RESERVED)
mvn test -Dtest=Rule3_*IdempotencyTest             # Case 3: Operation Log / Idempotency theo orderId
```

## Demo trực quan trên Web Dashboard
Mở `http://localhost:8080`:
- Dùng 2 Tab trên cùng để chuyển đổi giữa **1. TRƯỚC KHI CÓ SAGA** và **2. SAU KHI CÓ SAGA**.
- Chọn từng nút trong **3 Test Cases** (`Partial Commit`, `Dirty Read / Semantic Lock`, `Duplicate / Operation Log`) hoặc **Happy Path**:
  - **Bên trái:** Log giao tiếp thực tế giữa 4 services (`:8080`, `:8081`, `:8082`, `:8083`).
  - **Bên phải:** Panel Test Case tự kiểm tra dữ liệu thực — tự báo mã Bug/Anomaly và tô **ĐỎ** khi chạy ở chế độ Trước SAGA, chuyển **XANH** khi chạy ở chế độ Sau SAGA.

## API
- Dashboard: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- Postman: import `postman/SAGA_Pattern_Demo.postman_collection.json`

```bash
# Đặt hàng thành công
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-RICH","productId":"PROD-001","quantity":1,"amount":2000.00}'

# Kích hoạt compensation
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-POOR","productId":"PROD-002","quantity":1,"amount":1000.00}'

# Trạng thái hệ thống (kho, ví, đơn)
curl http://localhost:8080/api/system/snapshot

# Reset về dữ liệu ban đầu
curl -X POST http://localhost:8080/api/system/reset
```
