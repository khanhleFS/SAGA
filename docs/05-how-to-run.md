# 5. How to run

## Kiến trúc demo
```
        Dashboard (http://localhost:8080)
                   │ REST
                   ▼
        ORDER ORCHESTRATOR (:8080)
          Saga: reserve → debit → confirm
          Lỗi: compensate ngược lại
            │                      │
            ▼                      ▼
   INVENTORY (:8082)        PAYMENT (:8083)
```

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

## Chạy test
```bash
mvn test
```

## Demo 3 kịch bản
Mở dashboard `http://localhost:8080` và bấm từng nút, hoặc chạy `demo-scenarios.bat` / `demo-scenarios.sh`.

| Kịch bản | Kết quả |
|---|---|
| 1. Thành công (`CUST-RICH` mua `PROD-001`) | `COMPLETED` |
| 2. Bù trừ (`CUST-POOR` mua `PROD-002`) | Giữ kho OK → trừ tiền lỗi → **nhả kho** → `CANCELLED` |
| 3. Hết hàng (`PROD-OUT-OF-STOCK`) | Lỗi ngay bước 1 → `CANCELLED`, không gọi Payment |

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
