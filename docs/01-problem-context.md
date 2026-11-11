# 1. Problem & Context: Giao dịch Phân tán & Data Anomalies trong SAGA

## 1. Từ Monolith sang Microservices
Trong **Monolith**, toàn bộ ứng dụng dùng chung 1 Database. Khi đặt hàng, chỉ cần bọc trong `@Transactional`:
```sql
BEGIN TRANSACTION;
  Create Order;
  Deduct Inventory;
  Debit Wallet;
COMMIT; -- Lỗi ở bất kỳ đâu -> DB tự ROLLBACK sạch sẽ (Đảm bảo ACID)
```

Khi tách sang **Microservices** theo nguyên tắc **Database-per-Service**:
- `Order Service` (:8081) sở hữu dữ liệu đơn hàng.
- `Inventory Service` (:8082) sở hữu dữ liệu tồn kho.
- `Payment Service` (:8083) sở hữu dữ liệu ví tiền.

> **Lưu ý về bản Demo:** Thực tế mỗi service có một Database riêng. Trong **bản demo này không cần cài đặt DB**; mỗi service giữ một **In-Memory Store độc lập** (`ConcurrentHashMap` + `synchronized`) để mô phỏng Local Transaction tách rời và reset trạng thái nhanh khi thuyết trình.

---

## 2. Hai nhóm vấn đề khi Microservices thiếu Isolation (Theo Microsoft Azure Saga)

### A. Partial Commit (Mất tính Atomicity)
Mỗi service tự commit Local Transaction của mình. Nếu Bước 1 (`Order`), Bước 2 (`Inventory`) đã commit mà Bước 3 (`Payment`) thất bại $\rightarrow$ không có SQL `ROLLBACK` nào cứu được 2 service trước $\rightarrow$ **lệch dữ liệu** (kho mất hàng, đơn treo).

### B. Potential Data Anomalies (Bất thường dữ liệu do thiếu Isolation)
Vì không có khóa liên service (Built-in Isolation), các Saga chạy song song hoặc lỗi giữa chừng sẽ gây ra **3 hiện tượng bất thường điển hình**:

| Data Anomaly | Bản chất vấn đề | Ví dụ trong hệ thống đặt hàng |
|---|---|---|
| **1. Lost Updates** | Một Saga ghi đè hoặc làm mất cập nhật của một Saga khác vì không xét đến thay đổi đang diễn ra. | Saga A ghi đè số dư/tồn kho cố định hoặc trừ thẳng mà bỏ qua thay đổi của Saga B $\rightarrow$ mất dữ liệu cập nhật. |
| **2. Dirty Reads** | Một Saga hoặc transaction khác **đọc phải dữ liệu chưa hoàn tất** của Saga đang chạy (sau đó Saga đó có thể bị rollback/bù trừ). | Saga A vừa trừ thẳng kho xong nhưng chưa thanh toán; bên ngoài nhìn vào tưởng hàng đã bán đứt (`available` giảm), ngay sau đó Saga A thất bại ở Payment! |
| **3. Fuzzy (Nonrepeatable) Reads** | Các bước khác nhau trong cùng một Saga đọc ra **giá trị không nhất quán** vì có cập nhật chen vào giữa hai lần đọc. | Bước 1 kiểm tra giá/tồn kho là X, đến bước 3 thanh toán thì dữ liệu đã bị giao dịch khác sửa thành Y. |

*(Cộng thêm trong mạng phân tán: nếu lệnh bị gửi trùng khi timeout mà service không lưu vết thao tác theo `orderId` $\rightarrow$ trừ kho / trừ tiền 2 lần).*

---

## 3. Vì sao không dùng 2PC (Two-Phase Commit) để khóa cứng?
**2PC** mang `@Transactional` lên môi trường phân tán qua 2 pha (**Prepare** $\rightarrow$ **Commit/Rollback**) bằng cách **khóa chặt (lock)** dữ liệu ở mọi service suốt thời gian giao tiếp mạng.

**Lý do Microservices tránh dùng 2PC:**
- **Khóa lâu (Blocking Locks):** Thông lượng (throughput) tụt mạnh do giữ lock qua mạng.
- **SPOF:** Coordinator sập giữa chừng thì các service bị treo lock.
- **Phụ thuộc chặt:** Mọi service/DB phải hỗ trợ XA và online cùng lúc.

→ Thay vào đó, **SAGA Pattern** chấp nhận thiếu Isolation ở mức hạ tầng và dùng **Compensating Transactions + Các chiến lược chống Data Anomalies** ở mức ứng dụng (xem [02-saga-pattern.md](02-saga-pattern.md)).
