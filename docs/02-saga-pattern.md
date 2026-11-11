# 2. Saga Pattern: lý thuyết và các loại

## Saga là gì?
Saga là **chuỗi các local transaction**. Mỗi service commit ngay vào DB của mình.
Nếu một bước lỗi, Saga chạy **compensating transaction** (giao dịch bù trừ) cho các bước đã xong, theo **thứ tự ngược lại**.

```
T1 → T2 → T3 (lỗi!)  ⇒  C2 → C1
```

- Saga là **pattern kiến trúc**, không phải thư viện hay đoạn code cố định.
- **Compensation không phải rollback DB.** Các bước trước đã commit thật, nên phải làm hành động nghiệp vụ ngược lại.

| Bước | Forward | Compensation |
|---|---|---|
| Giữ hàng / phòng | `available - 1`, `reserved + 1` | `available + 1`, `reserved - 1` |
| Trừ tiền | Debit ví | Refund ví |
| Tạo booking | `PENDING` | `CANCELLED` + thông báo user |

## Mô hình nhất quán
Saga theo hướng **BASE**: đạt **eventual consistency**.
Saga **không có Isolation**, nên khi Saga đang chạy, transaction khác có thể thấy dữ liệu trung gian.
Cách giảm thiểu là dùng trạng thái giữ chỗ tạm (`PENDING`, `RESERVED`) trước khi chốt.

## Hai loại Saga

### Choreography (biên đạo)
Không có điều phối trung tâm. Mỗi service phát **event** lên message broker, service khác lắng nghe và tự làm bước của mình.

```
Booking ──BookingCreated──▶ Reservation ──RoomReserved──▶ Payment ──PaymentDone──▶ Booking(CONFIRMED)
```

### Orchestration (nhạc trưởng)
Một **Orchestrator** giữ luồng nghiệp vụ, ra **command** cho từng service và kích hoạt bù trừ khi có lỗi.

```
Orchestrator ──reserve──▶ Inventory
             ──debit────▶ Payment
             ──(lỗi) compensate──▶ Inventory
```

### Khi nào dùng loại nào?
| | Choreography | Orchestration |
|---|---|---|
| Điều khiển luồng | Phân tán (event) | Tập trung (orchestrator) |
| Phù hợp | Ít bước (2-3), hệ thống đơn giản | Nhiều bước, logic phân nhánh |
| Ưu điểm | Loose coupling, cấu hình đơn giản | Dễ theo dõi, dễ debug, dễ thêm bước |
| Nhược điểm | Khó theo dõi khi nhiều bước | Orchestrator cần chịu lỗi tốt |
| Cần broker? | Gần như bắt buộc | Không bắt buộc |

### Lưu ý: broker và kiểu Saga là hai việc khác nhau
- **Kiểu Saga** quyết định *ai điều khiển luồng*.
- **Cách giao tiếp** (REST hay broker/Kafka) quyết định *service nói chuyện bằng gì*.
- Orchestration có thể chạy bằng REST đồng bộ hoặc broker (command/reply).
- **Dự án này dùng Orchestration + REST** để dễ trực quan hóa khi demo.
