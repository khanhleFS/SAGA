# SAGA Pattern trong Spring Boot

Demo **Saga Orchestration** để quản lý giao dịch phân tán và giao dịch bù trừ (compensating transaction) trong kiến trúc microservices.

**Tech stack:** Java 21, Spring Boot 3.4.1, REST, Docker Compose, JUnit 5.

## Quick start
```bash
docker-compose up --build
```
Mở dashboard: http://localhost:8080

## Tài liệu
| # | Nội dung |
|---|---|
| 1 | [Problem & Context](docs/01-problem-context.md): Partial Commit & 3 Data Anomalies (Lost Updates, Dirty Reads, Fuzzy Reads) |
| 2 | [Saga Pattern](docs/02-saga-pattern.md): lý thuyết SAGA, Choreography vs Orchestration & 6 Countermeasures (Microsoft Azure) |
| 3 | [3 Test Cases Đối Chứng](docs/03-golden-rules.md): đối chứng Trước vs Sau (Atomicity, Semantic Lock, Operation Log Idempotency) |
| 4 | [Related Info](docs/04-related-info.md): tài liệu tham khảo gốc (Paper 1987, Microsoft, microservices.io) |
| 5 | [How to run](docs/05-how-to-run.md): chạy demo, chạy bộ test đối chứng, Postman |

## Cấu trúc project
```
common-dto/           DTO dùng chung
saga-orchestrator/    Nhạc trưởng: chỉ điều phối Saga (:8080)
order-service/        Đơn hàng (:8081)
inventory-service/    Kho (:8082)
payment-service/      Thanh toán (:8083)
postman/              Postman collection
```
