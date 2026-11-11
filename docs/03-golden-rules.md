# 3. 3 Test Cases Đối Chứng: Data Anomalies (Trước) vs Countermeasures (Sau)

Bám sát tài liệu chuẩn ở [01-problem-context.md](01-problem-context.md) và [02-saga-pattern.md](02-saga-pattern.md), code demo chứng minh trực tiếp **3 cặp đối chứng** (cùng nút **Happy Path**):

| Case | Bất thường / Lỗi khi CHƯA có SAGA (Before) | Chiến lược vá lỗi trong SAGA (After) | Test Class chứng minh |
|---|---|---|---|
| **Case 1** | **BUG-1: Partial Commit**<br>Payment lỗi nhưng `Order` & `Inventory` đã commit trước đó không tự hoàn tác $\rightarrow$ kho mất hàng, đơn treo. | **Compensating Transaction (LIFO) + Commutative Updates**<br>Orchestrator chạy bù trừ ngược `Release Inventory (+qty)` $\rightarrow$ `Cancel Order`. | `WithoutSagaProblemsTest`, `Rule1_AtomicityAndCompensationTest` |
| **Case 2** | **BUG-2: Dirty Read (Giao dịch B đọc khi A đang chạy dở)**<br>Giao dịch A gọi `POST /api/inventory/deduct` trừ thẳng `availableStock` (`reservedStock = 0`) lúc chưa thanh toán. Giao dịch B nhảy vào đọc giữa luồng thấy kho đã trừ đứt mà không biết A chưa trả tiền. | **Semantic Lock (`PENDING` / `RESERVED`)**<br>Giao dịch A gọi `POST /api/inventory/reserve` giữ tạm vào `reservedStock = 1`, `Order = PENDING`. Giao dịch B đọc vào biết ngay hàng đang khóa tạm, chỉ bước cuối mới `Confirm` hoặc `Release`. | `WithoutSagaProblemsTest`, `Rule2_SemanticLockIsolationTest` |
| **Case 3** | **BUG-3: Lost Update (Retry ghi đè / Trừ 2 lần tiền)**<br>Mạng timeout gửi retry: lần gọi thứ 2 đè lên trạng thái đã trừ của lần 1 vì thiếu Operation Log theo `orderId` $\rightarrow$ khách bị trừ kho & ví **2 lần**. | **Version File / Operation Log *(Idempotency)***<br>Lưu trạng thái từng `orderId` (`RESERVED`, `CONFIRMED`, `COMPENSATED`, `DEBITED`, `REFUNDED`) để chặn retry ghi đè. Gọi $N$ lần vẫn chỉ trừ tiền & kho đúng 1 lần. | `Rule3_InventoryIdempotencyTest`, `Rule3_PaymentIdempotencyTest` |

---

## Chi tiết luồng thực thi

### Case 1 — Partial Commit vs LIFO Compensation
- **Trước SAGA (`POST /api/orders/without-saga?mode=partial_commit`):** `CUST-POOR` ($50) đặt `PROD-002` ($1,000). Kho bị trừ (`5 -> 4`), Payment lỗi, luồng dừng luôn $\rightarrow$ kho mất 1 máy, đơn treo `PENDING`.
- **Sau SAGA (`POST /api/orders`):** Orchestrator lưu các bước bù trừ vào `Deque<Compensation>`. Khi `Payment` lỗi, `abort()` chạy ngược: `Release Inventory` (`4 -> 5`) $\rightarrow$ `Cancel Order` (`CANCELLED`).

### Case 2 — Dirty Read vs Semantic Lock
- **Trước SAGA (`POST /api/orders/without-saga?mode=no_semantic_lock`):** Gọi `POST /api/inventory/deduct` trừ thẳng `availableStock` (`5 -> 4, reserved = 0`). Không đánh dấu trạng thái giữ tạm cho giao dịch đang chạy.
- **Sau SAGA (`POST /api/orders?delayBeforePaymentMs=1400`):** Gọi `POST /api/inventory/reserve` chuyển sang `available = 4, reserved = 1` và `Order = PENDING` (**Semantic Lock**). Nếu bước sau thành công mới xóa `reservedStock` (Confirm), nếu lỗi thì trả lại `availableStock`.

### Case 3 — Duplicate Updates vs Operation Log (Áp dụng mở rộng từ Version File)
- **Trước SAGA (`POST /api/orders/without-saga?mode=duplicate_request`):** Gửi lặp lại `reserve` và `debit` 2 lần cho cùng `orderId` $\rightarrow$ kho mất 2 máy, ví bị trừ gấp đôi (`$5,000 -> $1,000`).
- **Sau SAGA (`POST /api/orders/duplicate-check`):** `InventoryService` kiểm tra `reservationStates`, `PaymentService` kiểm tra `paymentStates` theo `orderId` $\rightarrow$ lần gọi thứ 2 trả về `Idempotent OK`, dữ liệu chỉ thay đổi đúng 1 lần (`$5,000 -> $3,000`, kho trừ 1).
- **Lưu ý phân biệt lý thuyết vs Demo:** *Version File* theo định nghĩa gốc của Saga dùng để ghi lại các thao tác trên bản ghi nhằm sắp xếp lại đúng thứ tự thực thi (chống xung đột thứ tự); Demo mở rộng áp dụng Operation Log theo `orderId` để đảm bảo **Idempotency** khi mạng retry.

---

## Ghi chú thiết kế đi kèm (Pessimistic View & Resilience)
- **Pessimistic View (Step Ordering):** Luồng chuẩn luôn đặt `Inventory` trước `Payment -> Confirm` để hạn chế tối đa việc phải hoàn tiền.
- **Retry & DLQ:** Trong thực tế cần kết hợp retry (Orchestrator có sẵn `executeWithRetry`) và Dead Letter Queue cho các lệnh bù trừ.
