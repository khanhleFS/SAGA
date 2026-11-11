# 2. Saga Pattern: Lý thuyết, 2 Cách Implement & Chiến lược chống Data Anomalies

## 1. Lý thuyết cốt lõi của SAGA Pattern
**SAGA** chia một giao dịch phân tán thành **chuỗi các Local Transactions** ($T_1, T_2, \dots, T_n$):
- Mỗi bước $T_i$ tự commit ngay vào dữ liệu của service đó (không giữ lock liên service).
- Mỗi bước tiến $T_i$ (**Forward / Compensable Action**) đi kèm một bước lùi $C_i$ (**Compensating Action** — bù trừ nghiệp vụ).
- Nếu bước $T_k$ thất bại, SAGA tự động chạy chuỗi bù trừ **theo thứ tự ngược lại (LIFO)**: $C_{k-1} \rightarrow C_{k-2} \rightarrow \dots \rightarrow C_1$.

```
Chiều tiến (Forward):     T1 (Order) ──▶ T2 (Inventory) ──▶ T3 (Payment ❌ Lỗi!)
                                                                 │
Chiều lùi (Compensate):   C1 (Cancel) ◀── C2 (Release)  ◀────────┘
```

---

## 2. Hai cách implement SAGA

| Tiêu chí | Choreography (Biên đạo múa) | Orchestration (Nhạc trưởng) |
|---|---|---|
| **Cơ chế** | Phân tán: mỗi service xong việc tự bắn **Event** lên Broker (Kafka/RabbitMQ). | Tập trung: 1 **Orchestrator** gửi **Command** điều phối từng service và gọi bù trừ khi lỗi. |
| **Phù hợp** | Quy trình ngắn (2–3 service), đơn giản. | Quy trình nhiều bước, cần theo dõi trạng thái tập trung & dễ debug. |
| **Giao tiếp** | Gần như bắt buộc dùng Message Broker. | REST hoặc Broker đều được *(Demo dùng REST + Orchestrator đứng ngoài 3 services)*. |

---

## 3. Các chiến lược chống Data Anomalies (Theo Microsoft Azure Saga)

Để khắc phục 3 bất thường (*Lost Updates, Dirty Reads, Fuzzy Reads*) ở [01-problem-context.md](01-problem-context.md), tài liệu chuẩn của Microsoft & Chris Richardson đưa ra **6 Countermeasures**:

| Chiến lược (Countermeasure) | Cách hoạt động | Áp dụng trong Demo |
|---|---|---|
| **1. Semantic Lock** | Dùng khóa ở tầng ứng dụng (ví dụ `Order = PENDING`, `Inventory = reservedStock`) để báo hiệu dữ liệu đang trong một Saga chưa hoàn tất $\rightarrow$ chống **Dirty Read**. | **Có (Case 2):** Tách `availableStock` và `reservedStock`, `Order = PENDING`. |
| **2. Commutative Updates** | Thiết kế các phép cập nhật có tính giao hoán (`+amount` / `-amount`, `+qty` / `-qty`), chạy theo thứ tự nào cũng cho cùng kết quả $\rightarrow$ chống **Lost Updates**. | **Có (Case 1):** Bù trừ cộng/trừ delta (`+qty`, `+amount`), không ghi đè giá trị cứng. |
| **3. Version Files (Operation Log)** | Ghi log mọi thao tác theo `orderId` / version để đảm bảo các thao tác chạy đúng thứ tự, tránh xung đột và đảm bảo **Idempotency** khi retry. | **Có (Case 3):** Map `reservationStates` & `paymentStates` theo `orderId`. |
| **4. Pessimistic View** | Sắp xếp lại thứ tự Saga: đẩy các bước cập nhật nhạy cảm sang các bước retryable về sau, đặt bước dễ lỗi/dễ bù trừ (`Inventory`) lên trước (`Payment`) để giảm rủi ro Dirty Read. | **Có (Luồng chuẩn):** Chạy `Order -> Inventory -> Payment -> Confirm`. |
| **5. Reread Values** | Đọc lại giá trị trước khi cập nhật cuối; nếu dữ liệu đã bị Saga khác sửa đổi thì dừng và khởi động lại Saga $\rightarrow$ chống **Fuzzy Reads / Lost Updates**. | *Ghi chú lý thuyết (mở rộng khi dùng DB/Version).* |
| **6. Risk-based Concurrency** | Chọn cơ chế đồng bộ theo mức độ rủi ro nghiệp vụ (giao dịch rủi ro thấp dùng Saga, giao dịch rủi ro cực cao có thể dùng cơ chế khóa chặt). | *Ghi chú kiến trúc khi thiết kế hệ thống.* |

→ Chi tiết cách từng Case trên Demo chứng minh **Trước (Anomaly/Bug) vs Sau (Countermeasure)** xem tại [03-golden-rules.md](03-golden-rules.md).
