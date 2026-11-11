# 1. Problem & Context

## Monolith: một `@Transactional` là đủ
Toàn bộ ứng dụng dùng chung một database. Lỗi ở bước nào thì rollback toàn bộ (ACID).

```
BEGIN;
  Deduct Inventory;
  Debit Wallet;
  Create Order;
COMMIT;  -- hoặc ROLLBACK nếu có lỗi
```

## Microservices: Database-per-Service
`Order`, `Inventory`, `Payment` là các service độc lập, **mỗi service sở hữu database riêng**. Một transaction **không thể bao trùm nhiều service và nhiều database**.

> **Lưu ý cho phần demo:** để đơn giản, bản demo có thể chạy chung 1 DB vật lý. Khi đó mỗi service vẫn chỉ đụng bảng của mình và không đặt foreign key giữa các service. Đây chỉ là cách đơn giản hóa khi demo, không phải nguyên tắc của Saga.

## Vấn đề
Bước 1 đã commit, bước 2 lỗi thì dữ liệu bị lệch.

Ví dụ đặt phòng khách sạn: `Booking → Payment → Reservation`.
Khách đã trả tiền, nhưng lúc đặt phòng thì người khác nhanh tay hơn. Nếu không có cơ chế hoàn tác, khách **mất tiền và mất phòng**.

## Giải pháp "kiểu transactional": 2PC (Two-Phase Commit)
2PC cố gắng giải bài toán này đúng như cách `@Transactional` làm trong monolith, nhưng áp dụng cho nhiều service/DB: **hoặc tất cả cùng commit, hoặc tất cả cùng rollback**.

Có một **Coordinator** điều phối, chia 2 pha:
1. **Prepare:** Coordinator hỏi từng service "sẵn sàng commit chưa?". Mỗi service khóa dữ liệu và trả lời Yes/No.
2. **Commit/Rollback:** tất cả Yes thì ra lệnh Commit. Chỉ cần một No thì ra lệnh Rollback cho tất cả.

```
Coordinator ──Prepare?──▶ Order        (khóa, Yes)
            ──Prepare?──▶ Inventory    (khóa, Yes)
            ──Prepare?──▶ Payment      (khóa, No)
            ──Rollback──▶ tất cả
```

Nghe giống `@Transactional` phân tán, nhưng **ít dùng trong microservices** vì:
- **Khóa tài nguyên:** các service giữ lock từ Prepare đến Commit, qua cả độ trễ mạng, làm giảm throughput.
- **Single point of failure:** coordinator chết giữa chừng thì các service bị treo và giữ lock.
- **Phụ thuộc chặt:** mọi service phải hỗ trợ XA và cùng online. Khó scale.

| | 2PC | Saga |
|---|---|---|
| Cách làm | Khóa tất cả rồi commit đồng loạt | Mỗi bước commit ngay, lỗi thì bù trừ |
| Nhất quán | Mạnh (ACID) | Cuối cùng (eventual) |
| Isolation | Có | Không |
| Khả dụng, hiệu năng | Thấp | Cao |
| Gánh nặng | Hạ tầng XA | Dev tự viết compensation |

→ Cần một cách khác: **Saga Pattern** (xem [02-saga-pattern.md](02-saga-pattern.md)).
