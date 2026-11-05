---
marp: true
theme: default
paginate: true
header: "Microservices Architecture Seminar | SAGA Pattern"
footer: "Nhóm Seminar 2026 • Giảng viên & Hội đồng"
style: |
  section {
    background-color: #0d1117;
    color: #c9d1d9;
    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  }
  h1, h2, h3 {
    color: #58a6ff;
  }
  strong {
    color: #79c0ff;
  }
  code {
    background-color: #161b22;
    color: #7ee787;
  }
  blockquote {
    background: #161b22;
    border-left: 4px solid #f0883e;
    color: #8b949e;
  }
  table {
    font-size: 18px;
  }
---

# 🚀 SAGA PATTERN TRONG MICROSERVICES
## Quản lý Giao dịch Phân tán & Giao dịch Bù trừ (Compensating Transactions)

**Thời lượng thuyết trình:** 15 Phút  
**Công nghệ minh họa:** Java 21, Spring Boot 3.4, REST / Event-Driven  
**Người trình bày:** Nhóm Seminar Microservices  

> *"Làm thế nào để đảm bảo tính nhất quán dữ liệu (Data Consistency) khi mỗi microservice sở hữu một Database riêng biệt?"*

---

## 📌 NỘI DUNG TRÌNH BÀY (15 PHÚT)

| Thời gian | Nội dung | Mục tiêu |
| :--- | :--- | :--- |
| **00:00 - 03:00** | Vấn đề Giao dịch Phân tán & Giới hạn 2PC | Hiểu vì sao cần SAGA |
| **03:00 - 06:00** | Bản chất SAGA: Choreography vs Orchestration | Nắm vững lý thuyết cốt lõi |
| **06:00 - 08:00** | Thiết kế Kiến trúc Hệ thống Demo | Hiểu flow 3 services |
| **08:00 - 13:00** | **LIVE DEMO 3 KỊCH BẢN (Dashboard Web & Logs)** | Thấy tận mắt Compensation |
| **13:00 - 15:00** | Thách thức thực tế (Idempotency) & Q&A | Tổng kết và phản biện |

---

## 💥 1. BÀI TOÁN: GIAO DỊCH PHÂN TÁN (DISTRIBUTED TRANSACTION)

* Trong Monolith:
  * Tất cả dùng chung **1 Database duy nhất**.
  * Dùng `@Transactional` (ACID) của RDBMS rất đơn giản và an toàn.

* Trong Microservices (Pattern: *Database-per-Service*):
  * **Order Service** &rarr; Order DB
  * **Inventory Service** &rarr; Inventory DB
  * **Payment Service** &rarr; Payment DB

> ⚠️ **Nghịch lý:** Không thể dùng một SQL Transaction thông thường để commit đồng thời trên 3 database vật lý độc lập qua mạng!

---

## 🛑 2. TẠI SAO KHÔNG DÙNG 2PC (TWO-PHASE COMMIT)?

Nhiều người nghĩ đến chuẩn **XA / 2-Phase Commit (2PC)**:
1. **Prepare Phase:** Coordinator hỏi tất cả các node: "Sẵn sàng commit chưa?"
2. **Commit Phase:** Nếu tất cả say YES &rarr; Thực hiện Commit.

### Nhược điểm chí mạng của 2PC trong Microservices:
* 🔴 **Blocking Protocol:** Khóa tài nguyên (locks) trong suốt thời gian chờ mạng &rarr; Giảm thông lượng khủng khiếp.
* 🔴 **Single Point of Failure (SPOF):** Coordinator chết &rarr; Toàn bộ hệ thống treo.
* 🔴 **Vi phạm định lý CAP:** Ép hệ thống theo hướng CP, mất tính High Availability (A). Không phù hợp Cloud Native!

---

## 💡 3. SAGA PATTERN LÀ GÌ?

> *"SAGA được đề xuất lần đầu bởi Hector Garcia-Molina & Kenneth Salem (1987) cho Long-Lived Transactions."*

* Thay vì 1 giao dịch phân tán toàn cục khổng lồ, SAGA chia thành **chuỗi các Local Transactions**.
* Mỗi local transaction chỉ cập nhật database riêng của service đó và phát ra event/gọi bước kế tiếp.
* Nếu một bước bất kỳ bị **THẤT BẠI**, SAGA sẽ kích hoạt các **GIAO DỊCH BÙ TRỪ (Compensating Transactions)** theo chiều ngược lại để hoàn tác (Undo) các bước đã thành công trước đó.
* Tuân theo mô hình **BASE** (Basically Available, Soft state, Eventual consistency).

---

## 🔄 4. GIAO DỊCH BÙ TRỪ (COMPENSATING TRANSACTION)

* Trong ACID SQL: Có thể thực hiện `ROLLBACK` vật lý để xóa sạch thay đổi.
* Trong SAGA: **KHÔNG THỂ ROLLBACK VẬT LÝ** (vì transaction của các bước trước đã `COMMIT` vào DB rồi!).
* **Giải pháp: Semantic Rollback (Bù trừ về mặt nghiệp vụ)**

```
Bước tiến (Forward Action)      ==>     Giao dịch bù trừ (Compensating Action)
--------------------------------------------------------------------------------
Trừ 1 sản phẩm khỏi kho       ==>     Cộng trả 1 sản phẩm lại vào kho
Trừ 2,000,000đ trong ví       ==>     Hoàn tiền 2,000,000đ vào ví
Tạo đơn hàng PENDING          ==>     Cập nhật đơn hàng thành CANCELLED
Gửi Email thông báo thành công ==>     Gửi Email báo hủy đơn & xin lỗi
```

---

## ⚖️ 5. PHÂN LOẠI SAGA: CHOREOGRAPHY VS ORCHESTRATION

| Tiêu chí | Choreography (Biên đạo múa) | Orchestration (Nhạc trưởng) |
| :--- | :--- | :--- |
| **Cơ chế điều phối** | Phi tập trung (Decentralized). Các service tự nghe/bắn Event qua Message Broker (RabbitMQ/Kafka). | Tập trung (Centralized). Một **Orchestrator** đứng ra điều khiển từng bước. |
| **Độ phức tạp** | Đơn giản khi ít service (2-3 service). Rất dễ rối rắm hình "spaghetti" khi hệ thống lớn. | Rõ ràng, dễ theo dõi State Machine, dễ debug và bảo trì. |
| **Điểm lỗi tập trung** | Không có SPOF. | Cần đảm bảo HA cho Orchestrator. |
| **Phù hợp** | Hệ thống nhỏ, luồng ít bước. | **Hệ thống thương mại điện tử, tài chính, luồng phức tạp.** |

*(Trong buổi seminar hôm nay, nhóm chọn **Orchestration Saga** vì tính minh bạch và trực quan cao nhất).*

---

## 🏛️ 6. KIẾN TRÚC HỆ THỐNG DEMO

Hệ thống mô phỏng bài toán **E-Commerce Order Fulfillment**:

```
 [CLIENT / WEB DASHBOARD :8080]
              │
              ▼
   ┌──────────────────────────────────────────────┐
   │         ORDER ORCHESTRATOR (:8080)           │
   │  - Quản lý Saga State Machine                │
   │  - Theo dõi Trace Logs & Điều phối bù trừ    │
   └──────────────┬───────────────────────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
┌──────────────────┐ ┌──────────────────┐
│ INVENTORY (:8082)│ │  PAYMENT (:8083) │
│ - Giữ kho        │ │ - Trừ số dư ví   │
│ - Bù trừ hồi kho │ │ - Bù trừ hoàn ví │
└──────────────────┘ └──────────────────┘
```

---

## 🎬 7. BA KỊCH BẢN SEMINAR SẼ DEMO

### 🟢 Kịch bản 1: Happy Path (Giao dịch hoàn tất thành công)
* Khách giàu (`$5,000`) mua MacBook (`$2,000`).
* Bước 1: Giữ kho &rarr; **SUCCESS**.
* Bước 2: Trừ tiền &rarr; **SUCCESS**.
* Order đổi trạng thái &rarr; `COMPLETED`.

---

## 🎬 7. BA KỊCH BẢN SEMINAR SẼ DEMO (Tiếp)

### 🔴 Kịch bản 2: Payment Thất Bại & Kích Hoạt Compensation (Trọng tâm SAGA)
* Khách nghèo (`$50`) mua iPhone (`$1,000`).
* Bước 1: Giữ kho &rarr; **SUCCESS** (Kho đã trừ 1 máy).
* Bước 2: Gọi Payment &rarr; **FAILED** (Số dư không đủ!).
* ⚠️ **Kích hoạt SAGA Compensation:** Orchestrator gọi lệnh `releaseStock()` trả lại 1 máy về kho &rarr; Order = `CANCELLED`.
* **Kết quả:** Kho và Ví bảo toàn toàn vẹn dữ liệu, không bị thất thoát hàng!

### 🟡 Kịch bản 3: Early Failure (Hết hàng tại Bước 1)
* Mua sản phẩm tồn kho = 0 &rarr; Thất bại ngay Bước 1 &rarr; Không gọi Payment.

---

## 💻 8. LIVE DEMO (THỰC NGHIỆM TRÊN TRÌNH DUYỆT & TERMINAL)

*(Chuyển sang màn hình Web Dashboard: `http://localhost:8080`)*

**Các điểm người nghe cần quan sát:**
1. **Trực quan hóa:** Các ô Node trên sơ đồ đổi màu (Xanh: Thành công, Vàng: Đang bù trừ, Đỏ: Thất bại).
2. **Số liệu biến động theo thời gian thực:**
   * Tồn kho khả dụng & Tồn kho đang giữ.
   * Số dư ví khách hàng trước và sau giao dịch.
3. **Console Log:**
   * Xem vết log `[SAGA STEP 1]`, `[SAGA STEP 2]`, và `[SAGA COMPENSATION TRIGGERED]`.

---

## 🛡️ 9. NHỮNG BÀI TOÁN THỰC TẾ KHI TRIỂN KHAI SAGA

### 1. Tính Lũy Đẳng (Idempotency)
* Khi mạng chập chờn, lệnh bù trừ có thể bị gọi nhiều lần &rarr; Service phải đảm bảo gọi 1 lần hay $N$ lần thì kết quả vẫn như nhau (dùng `orderId` làm idempotency key).

### 2. Thiếu tính Isolation (ACID mà không có "I")
* Trong lúc Saga đang chạy, dữ liệu tạm có thể bị nhìn thấy bởi transaction khác (Dirty Read).
* *Giải pháp:* Dùng kỹ thuật **Pessimistic Locking / Pending State** (như trạng thái `RESERVED` trong demo).

### 3. Outbox Pattern
* Đảm bảo việc ghi Database và gửi Event/Message diễn ra nguyên tử (Atomic).

---

## 📋 10. KẾT LUẬN & BÀI HỌC KINH NGHIỆM

* SAGA là chuẩn kiến trúc công nghiệp (Industry Standard) cho giao dịch phân tán trong Microservices.
* Đánh đổi tính **Nhất quán tức thì (Immediate Consistency)** để lấy **Khả năng mở rộng (Scalability)** và **Tính sẵn sàng cao (High Availability)**.
* **Quy tắc vàng khi thiết kế:**
  1. Mọi bước tiến (Forward) luôn phải có bước lùi (Compensate) tương ứng.
  2. Mọi API bù trừ bắt buộc phải có tính Lũy Đẳng (Idempotent).
  3. Phải có hệ thống Log / Saga Execution Coordinator để kiểm toán và truy vết.

---

## ❓ 11. HỎI ĐÁP (Q & A)

**Cảm ơn Thầy/Cô và các bạn đã lắng nghe!**

* Repository GitHub: `https://github.com/<your-username>/saga-pattern-microservices-demo`
* Công nghệ: Spring Boot 3.4, Java 21, Docker Compose

*Nhóm sẵn sàng trả lời các câu hỏi phản biện từ Hội đồng!*

