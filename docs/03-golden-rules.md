# 3. Golden Rules khi viết microservice tham gia Saga

| # | Rule | Ý nghĩa |
|---|---|---|
| 1 | **Mỗi bước có một compensation** | Giữ phòng thì nhả phòng, trừ tiền thì refund. |
| 2 | **Compensation là rollback nghiệp vụ** | Không phải rollback DB, vì bước trước đã commit. |
| 3 | **Idempotent** | Nhận cùng một request nhiều lần thì kết quả như một lần. Dùng `orderId` làm idempotency key. |
| 4 | **Compensation phải thành công** | Retry (backoff), Dead Letter Queue hoặc cảnh báo để xử lý thủ công. |
| 5 | **Bước dễ lỗi đặt trước, bước khó hoàn tác đặt sau** | Giữ chỗ trước, thu tiền sau, để ít phải refund. |
| 6 | **Giữ chỗ tạm trước khi chốt** | Dùng `PENDING`/`RESERVED` để giảm xung đột do thiếu Isolation. |
| 7 | **Lưu trạng thái Saga** | Biết đang ở bước nào và recover được khi orchestrator crash. |
| 8 | **Chấp nhận eventual consistency** | Trạng thái trung gian là bình thường, kết quả cuối mới nhất quán. |
| 9 | **Mỗi service sở hữu dữ liệu riêng, không dùng foreign key giữa service** | Tham chiếu bằng ID. Khi demo có thể dùng chung 1 DB, nhưng vẫn không đặt FK giữa các service. |

## Ví dụ: đặt phòng khách sạn
`Booking(PENDING) → Reservation (giữ phòng) → Payment → Booking(CONFIRMED)`

| Tình huống | Xử lý |
|---|---|
| Phòng đã bị người khác đặt | Reservation lỗi → `Booking(CANCELLED)` → báo user. Chưa thu tiền nên không cần refund. |
| Payment lỗi sau khi giữ phòng | Bù trừ: nhả phòng → `Booking(CANCELLED)`. |
| Phòng bị mất sau khi đã trả tiền | Bù trừ ngược: **Refund** → hủy booking → báo user. |

## Các rule này nằm ở đâu trong code?
| Rule | Trạng thái trong project |
|---|---|
| 1, 2 | Có: `reserveStock`/`compensateReleaseStock`, `debitCustomer`/`compensateRefund` |
| 3 | Mới có ở phần compensation. **Forward step (`reserve`, `debit`) chưa idempotent.** |
| 4 | Chưa có retry. Nếu bù trừ lỗi, hệ thống chỉ ghi log "manual intervention". |
| 5 | Có: reserve kho trước, trừ tiền sau |
| 6 | Có: `availableStock`/`reservedStock` |
| 7 | Chỉ lưu in-memory (`orderRepository`), chưa lưu DB |
| 8, 9 | Là hướng thiết kế. Dữ liệu hiện chưa dùng DB. |

## FAQ nhanh
**Saga có đủ ACID không?**
Có A (nhờ bù trừ), C (eventual), D (đã commit). **Không có I (Isolation).**

**Bù trừ bị lỗi thì sao?**
Retry với backoff, đẩy vào DLQ nếu quá số lần, cảnh báo ops xử lý thủ công.

**Vì sao cần idempotency?**
Timeout hoặc retry có thể khiến một lệnh bị gửi nhiều lần. Service phải cho kết quả giống nhau dù chạy 1 hay N lần.
