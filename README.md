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
| 1 | [Problem & Context](docs/01-problem-context.md): vì sao cần Saga |
| 2 | [Saga Pattern](docs/02-saga-pattern.md): lý thuyết, các loại, khi nào dùng |
| 3 | [Golden Rules](docs/03-golden-rules.md): điều cần quan tâm khi viết microservice |
| 4 | [Related Info](docs/04-related-info.md): tài liệu tham khảo |
| 5 | [How to run](docs/05-how-to-run.md): chạy demo, test, Postman |

## Cấu trúc project
```
common-dto/           DTO dùng chung
order-orchestrator/   Điều phối Saga (:8080)
inventory-service/    Kho (:8082)
payment-service/      Thanh toán (:8083)
postman/              Postman collection
```
