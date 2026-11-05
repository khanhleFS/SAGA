# 🚀 SEMINAR MICROSERVICES: SAGA PATTERN TRONG SPRING BOOT

> **Đề tài:** Quản trị Giao dịch Phân tán (Distributed Transactions) và Cơ chế Giao dịch Bù trừ (Compensating Transactions) bằng SAGA Pattern trong Kiến trúc Microservices.  
> **Thời lượng báo cáo:** 15 Phút (Bao gồm Thuyết trình lý thuyết & Live Demo thực nghiệm).  
> **Tech Stack:** Java 21 LTS, Spring Boot 3.4.1, RESTful APIs / Orchestrator Engine, HTML5/JS Live Dashboard, Docker Compose, JUnit 5.

---

## 📑 MỤC LỤC
1. [Đặt Vấn Đề: Bài Toán Giao Dịch Phân Tán](#1-đặt-vấn-đề-bài-toán-giao-dịch-phân-tán)
2. [Vì Sao Không Dùng 2PC (Two-Phase Commit)?](#2-vì-sao-không-dùng-2pc-two-phase-commit)
3. [Bản Chất Cốt Lõi Của SAGA Pattern](#3-bản-chất-cốt-lõi-của-saga-pattern)
4. [So Sánh: Choreography vs Orchestration](#4-so-sánh-choreography-vs-orchestration)
5. [Kiến Trúc Hệ Thống Demo](#5-kiến-trúc-hệ-thống-demo)
6. [Hướng Dẫn Cài Đặt & Khởi Chạy](#6-hướng-dẫn-cài-đặt--khởi-chạy)
7. [Kịch Bản Thuyết Trình & Live Demo 15 Phút](#7-kịch-bản-thuyết-trình--live-demo-15-phút-chuẩn-seminar)
8. [Tài Liệu API & Postman](#8-tài-liệu-api--postman)
9. [Bộ Câu Hỏi Phản Biện Thường Gặp (FAQ với Giảng Viên)](#9-bộ-câu-hỏi-phản-biện-thường-gặp-faq-với-giảng-viên)

---

## 1. ĐẶT VẤN ĐỀ: BÀI TOÁN GIAO DỊCH PHÂN TÁN

Trong kiến trúc truyền thống **Monolith**, toàn bộ ứng dụng dùng chung một Cơ sở Dữ liệu (RDBMS). Khi tạo đơn hàng, ta chỉ cần bọc logic vào `@Transactional`:
```
BEGIN TRANSACTION;
  Deduct Inventory;
  Debit Wallet;
  Create Order;
COMMIT; -- Hoặc ROLLBACK nếu có lỗi
```
Giao dịch đảm bảo hoàn hảo 4 tính chất **ACID** (Atomicity, Consistency, Isolation, Durability).

Tuy nhiên, khi chuyển sang **Microservices** tuân thủ nguyên tắc **Database-per-Service**:
* `Order Service` sở hữu **Order Database**
* `Inventory Service` sở hữu **Inventory Database**
* `Payment Service` sở hữu **Payment Database**

❌ **Vấn đề xuất hiện:** Không thể dùng một SQL Transaction thông thường để quản lý các transaction nằm trên 3 server và 3 database vật lý tách rời qua mạng. Nếu bước trừ kho thành công nhưng bước thanh toán thất bại, hệ thống sẽ rơi vào trạng thái bất nhất (Data Inconsistency - mất hàng mà không nhận được tiền).

---

## 2. VÌ SAO KHÔNG DÙNG 2PC (TWO-PHASE COMMIT)?

Chuẩn giao dịch phân tán cổ điển **XA / 2-Phase Commit (2PC)** có Coordinator hỏi từng node: *Prepare -> Commit*. Mô hình này **không khả thi** trong Microservices hiện đại vì:
1. **Khóa tài nguyên (Blocking):** Tất cả database phải giữ lock tài nguyên trong suốt thời gian giao tiếp mạng, khiến thông lượng (Throughput) giảm nghiêm trọng.
2. **Điểm lỗi tập trung (SPOF):** Nếu Coordinator bị crash giữa chừng, các node tham gia sẽ bị treo (Hold locks vô thời hạn).
3. **Vi phạm định lý CAP:** 2PC ưu tiên tính CP (Consistency), hy sinh tính Availability (A) và Partition Tolerance (P) — đi ngược lại triết lý phân tán chịu lỗi của Cloud-Native.

---

## 3. BẢN CHẤT CỐT LÕI CỦA SAGA PATTERN

SAGA Pattern giải quyết bài toán theo hướng **BASE** (Basically Available, Soft-state, Eventual Consistency):
* Chia một giao dịch phân tán lớn thành **chuỗi các Local Transactions**.
* Mỗi service thực thi local transaction trong database của chính mình và commit ngay lập tức.
* Nếu có bất kỳ bước nào thất bại, SAGA sẽ kích hoạt **Giao Dịch Bù Trừ (Compensating Transactions)** để hoàn tác (Undo) theo chiều ngược lại.

### Bù trừ Nghiệp vụ (Semantic Rollback)
Vì local transaction của bước trước đã commit vào database, ta **không thể rollback vật lý** kiểu SQL. Ta phải thực hiện một hành động nghiệp vụ bù trừ:
* **Hành động tiến (Forward Action):** Giữ 1 sản phẩm trong kho (`availableStock - 1`, `reservedStock + 1`).
* **Hành động bù trừ (Compensating Action):** Trả lại 1 sản phẩm về kho (`availableStock + 1`, `reservedStock - 1`).

---

## 4. SO SÁNH: CHOREOGRAPHY VS ORCHESTRATION

| Tiêu Chí | Choreography (Biên Đạo Múa) | Orchestration (Nhạc Trưởng) |
| :--- | :--- | :--- |
| **Cơ chế** | Phân tán: Các service tự nghe và bắn Event qua Message Broker (RabbitMQ, Kafka). | Tập trung: Một **Orchestrator** điều phối gửi lệnh (Command) đến từng service. |
| **Ưu điểm** | Không có điểm điều khiển trung tâm, loose coupling cao cho quy trình đơn giản. | Luồng nghiệp vụ tập trung tại một nơi, dễ theo dõi State Machine, dễ debug, dễ giám sát. |
| **Nhược điểm** | Dễ biến thành "Spaghetti Event Architecture" khi hệ thống mở rộng nhiều bước. | Cần thiết kế Orchestrator chịu lỗi và có tính HA. |
| **Ứng dụng phù hợp** | Quy trình ngắn 2 - 3 bước đơn giản. | **Thương mại điện tử, xử lý thanh toán, đơn hàng đa bước phức tạp.** |

> 💡 **Lựa chọn cho Seminar:** Nhóm triển khai theo mô hình **Saga Orchestration** để giảng viên và sinh viên có thể trực quan hóa toàn bộ tiến trình State Machine và quan sát trực tiếp cơ chế Compensation theo thời gian thực.

---

## 5. KIẾN TRÚC HỆ THỐNG DEMO

Hệ thống mô phỏng quy trình đặt hàng và thanh toán trực tuyến gồm 3 Microservices độc lập:

```
                      ┌───────────────────────────────────────────────┐
                      │          WEB DASHBOARD (Port 8080)            │
                      │  - Giao diện trực quan hóa SAGA thời gian thực │
                      │  - Bấm 1-click chạy 3 kịch bản demo          │
                      └──────────────────────┬────────────────────────┘
                                             │ REST API
                                             ▼
                      ┌───────────────────────────────────────────────┐
                      │          ORDER ORCHESTRATOR (:8080)           │
                      │  - Saga State Machine: PENDING -> COMPLETED   │
                      │  - Tự động kích hoạt bù trừ khi bước sau lỗi  │
                      │  - Ghi vết SAGA Trace Log chi tiết            │
                      └──────────────┬─────────────────┬──────────────┘
                                     │                 │
                Bước 1 (Reserve)     │                 │ Bước 2 (Debit)
                Hoặc Bù Trừ (Release)│                 │ Hoặc Bù Trừ (Refund)
                                     ▼                 ▼
             ┌───────────────────────────────┐ ┌──────────────────────────────┐
             │    INVENTORY SERVICE (:8082)  │ │    PAYMENT SERVICE (:8083)   │
             │ - Quản lý tồn kho sản phẩm    │ │ - Quản lý ví khách hàng      │
             │ - Forward: Giữ hàng           │ │ - Forward: Trừ tiền          │
             │ - Compensate: Trả lại hàng    │ │ - Compensate: Hoàn tiền      │
             └───────────────────────────────┘ └──────────────────────────────┘
```

### Dữ liệu mẫu khởi tạo sẵn:
* **Sản phẩm (Inventory):**
  * `PROD-001` - MacBook Pro M3 (Tồn kho: 10 máy, Giá: $2,000)
  * `PROD-002` - iPhone 15 Pro (Tồn kho: 5 máy, Giá: $1,000)
  * `PROD-OUT-OF-STOCK` - PS5 Pro (Tồn kho: 0 máy - Dùng demo hết hàng)
* **Khách hàng (Payment):**
  * `CUST-RICH` - Nguyễn Văn A (Số dư: **$5,000.00**) &rarr; Đủ tiền mua mọi thứ.
  * `CUST-POOR` - Trần Văn B (Số dư: **$50.00**) &rarr; Demo thiếu tiền kích hoạt Compensation.

---

## 6. HƯỚNG DẪN CÀI ĐẶT & KHỞI CHẠY

### Yêu cầu môi trường:
* **Java:** JDK 21 trở lên (Đã xác minh tương thích hoàn toàn).
* **Maven:** 3.8+ (Hoặc dùng Docker Compose).
* **Docker & Docker Compose:** (Khuyên dùng để chạy 1 lệnh ăn ngay).

---

### Cách 1: Chạy Siêu Tốc Bằng Docker Compose (Khuyên dùng)
Chỉ cần 1 câu lệnh duy nhất từ thư mục gốc dự án:
```bash
docker-compose up --build
```
Hệ thống sẽ tự động build và dựng cả 3 container:
* `saga-order-orchestrator`: `http://localhost:8080`
* `saga-inventory-service`: `http://localhost:8082`
* `saga-payment-service`: `http://localhost:8083`

---

### Cách 2: Chạy Bằng Maven Trực Tiếp (Local)

1. **Build toàn bộ dự án:**
```bash
mvn clean package -DskipTests
```

2. **Khởi chạy cả 3 services:**
* Trên **Windows**: Bấm đúp hoặc chạy script:
  ```cmd
  start-all.bat
  ```
* Trên **Linux / macOS**:
  ```bash
  chmod +x start-all.sh
  ./start-all.sh
  ```

---

## 7. KỊCH BẢN THUYẾT TRÌNH & LIVE DEMO 15 PHÚT (CHUẨN SEMINAR)

Dưới đây là kịch bản phân bổ thời gian chuẩn xác từng phút giúp nhóm kiểm soát thời lượng thuyết trình hoàn hảo:

| Phút | Phần Trình Bày | Thao Tác Của Nhóm |
| :---: | :--- | :--- |
| **00:00 - 02:00** | **Đặt vấn đề:** Tại sao Database-per-service làm hỏng ACID? Giới hạn của 2PC. | Chiếu Slide 1 - 3 trong `SLIDES.md`. |
| **02:00 - 05:00** | **Lý thuyết SAGA:** Chuỗi Local Transactions & Khái niệm Compensating Transactions. So sánh Choreography vs Orchestration. | Chiếu Slide 4 - 6. Nhấn mạnh "Semantic Rollback". |
| **05:00 - 07:00** | **Kiến trúc Demo:** Giới thiệu 3 Microservices (Order :8080, Inventory :8082, Payment :8083). | Chiếu Slide 7 - 8. |
| **07:00 - 12:00** | **LIVE DEMO 3 KỊCH BẢN TRÊN WEB DASHBOARD:** | Mở trình duyệt tại `http://localhost:8080` |
| | **🟢 Kịch bản 1 (Happy Path):** Khách giàu mua MacBook $2,000. Cả 2 service xanh, Order chuyển `COMPLETED`. | Bấm nút **"Kịch bản 1: Thành Công"**. Quan sát số dư ví bị trừ từ $5000 &rarr; $3000, kho trừ 1 máy. |
| | **🔴 Kịch bản 2 (SAGA COMPENSATION - Trọng tâm):** Khách nghèo ($50) mua iPhone ($1,000). Bước 1 kho giữ hàng thành công &rarr; Bước 2 ví báo lỗi &rarr; SAGA kích hoạt Compensate hoàn trả máy về kho. | Bấm nút **"Kịch bản 2: Bù Trừ"**. Quan sát Node Kho đổi màu Vàng (Bù trừ), log console hiện `[SAGA COMPENSATION TRIGGERED]`, tồn kho khả dụng tự động hồi phục về nguyên trạng! |
| | **🟡 Kịch bản 3 (Early Failure):** Mua PS5 Pro hết hàng. Lỗi ngay tại bước 1, dừng an toàn không gọi sang Payment. | Bấm nút **"Kịch bản 3: Hết Hàng"**. |
| **12:00 - 13:30** | **Thách thức thực tế:** Tính Lũy Đẳng (Idempotency), Outbox Pattern. | Chiếu Slide 9 - 10. |
| **13:30 - 15:00** | **Tổng kết & Q&A:** Trả lời chất vấn của Giảng viên. | Chiếu Slide 11. |

---

## 8. TÀI LIỆU API & POSTMAN

### 1. Web Dashboard & Swagger UI:
* **Interactive Live Dashboard:** [http://localhost:8080](http://localhost:8080)
* **Swagger OpenAPI UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

### 2. Postman Collection:
File Postman đã được cấu hình sẵn trong thư mục:  
📁 `postman/SAGA_Pattern_Demo.postman_collection.json` (Import trực tiếp vào Postman).

### 3. Các Lệnh cURL Thử Nghiệm Nhanh:

#### Kịch bản 1: Đặt hàng thành công
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-RICH","productId":"PROD-001","quantity":1,"amount":2000.00}'
```

#### Kịch bản 2: Kích hoạt Giao dịch Bù trừ (Compensation)
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-POOR","productId":"PROD-002","quantity":1,"amount":1000.00}'
```

#### Lấy trạng thái Live của toàn hệ thống (Kho, Ví, Đơn):
```bash
curl http://localhost:8080/api/system/snapshot
```

#### Reset toàn hệ thống về trạng thái ban đầu:
```bash
curl -X POST http://localhost:8080/api/system/reset
```

---

## 9. BỘ CÂU HỎI PHẢN BIỆN THƯỜNG GẶP (FAQ VỚI GIẢNG VIÊN)

Dưới đây là 4 câu hỏi "tủ" giảng viên rất hay hỏi khi chấm seminar đề tài SAGA và câu trả lời chuẩn xác:

### ❓ Câu 1: SAGA có đảm bảo tính ACID đầy đủ không?
> **Trả lời:** SAGA đảm bảo được tính **A** (Atomicity - nhờ giao dịch bù trừ), tính **C** (Consistency - theo mô hình Eventual Consistency), và tính **D** (Durability - vì local transaction đã commit).  
> Tuy nhiên, SAGA **KHÔNG CÓ TÍNH ISOLATION (I)**! Trong thời gian Saga đang chạy, một transaction khác có thể đọc thấy dữ liệu tạm thời (Dirty Read). Để giảm thiểu điều này, ta áp dụng kỹ thuật **Pessimistic Locking / Pending States** (ví dụ trong demo: tách riêng `availableStock` và `reservedStock`, hàng chỉ chuyển thành trừ hẳn khi toàn bộ Saga thành công).

### ❓ Câu 2: Nếu chính bước Bù Trừ (Compensating Transaction) bị lỗi thì xử lý thế nào?
> **Trả lời:** Compensating transaction bắt buộc **phải thành công bằng mọi giá**. Các giải pháp thực tế:
> 1. **Retry với Exponential Backoff:** Thử lại nhiều lần cho đến khi thành công.
> 2. **Dead Letter Queue (DLQ):** Đẩy thông điệp lỗi vào hàng đợi DLQ nếu retry quá số lần cho phép.
> 3. **Manual Intervention & Alert:** Bắn cảnh báo cho đội ngũ vận hành (DevOps / Support) can thiệp sửa đổi dữ liệu thủ công.

### ❓ Câu 3: Tại sao tính Lũy Đẳng (Idempotency) là bắt buộc trong SAGA?
> **Trả lời:** Trong mạng phân tán, hiện tượng chập chờn hoặc timeout có thể khiến Orchestrator gửi lại một yêu cầu bù trừ nhiều lần. Service nhận (như Inventory hoặc Payment) phải có tính Lũy Đẳng: Thực thi 1 lần hay $N$ lần thì kết quả cuối cùng trên database vẫn giữ nguyên (Dùng `orderId` làm Idempotency Key để nhận biết giao dịch đã được xử lý rồi).

### ❓ Câu 4: Khi nào nên dùng Choreography, khi nào nên dùng Orchestration?
> **Trả lời:**
> * Chọn **Choreography** khi luồng giao dịch đơn giản (2-3 service), nhóm phát triển phân tán muốn tự chủ hoàn toàn các microservice mà không phụ thuộc service trung gian.
> * Chọn **Orchestration** khi quy trình nghiệp vụ phức tạp, nhiều bước phân nhánh, cần kiểm soát trạng thái tập trung chặt chẽ, dễ truy vết kiểm toán và dễ bổ sung các bước mới mà không phải sửa logic ở tất cả các service khác.

---

## 👥 THÔNG TIN NHÓM THỰC HIỆN
* **Môn học:** Kiến Trúc Hệ Thống Phân Tán / Microservices Architecture
* **Slide trình bày:** Xem chi tiết tại file [SLIDES.md](SLIDES.md) (Tương thích định dạng Marp / Markdown Presentation).
* **Source code:** Đầy đủ Unit Test, Mockito Stub, Clean Architecture sẵn sàng share trên GitHub.
