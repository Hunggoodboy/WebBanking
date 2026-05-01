🏦 Web Banking - Hệ thống Ngân hàng Phân tán (Distributed Core Banking)
Tác giả: Nguyễn Xuân Hùng - B23DCCN361

Đơn vị: Học viện Công nghệ Bưu chính Viễn thông (PTIT)

Vai trò: Team Leader / Backend & Big Data Engineer

📖 Giới thiệu dự án
Web Banking là một hệ thống mô phỏng Core Banking được thiết kế theo kiến trúc Hybrid Distributed (Kết hợp OLTP và OLAP). Dự án giải quyết các bài toán hóc búa trong hệ thống phân tán bao gồm: phân mảnh dữ liệu (Sharding), đồng bộ giao dịch liên miền (Distributed Transactions), và tích hợp hệ sinh thái Big Data để xử lý, phân tích dữ liệu lịch sử quy mô lớn.

🚀 Công nghệ sử dụng (Tech Stack)
Backend: Java 17, Spring Boot 3, Spring Data JPA.

Database (OLTP): Cụm 04 node SQL Server (1 Central + 3 Regional Nodes: North, Mid, South).

Message Broker: Apache Kafka.

Big Data (OLAP): Apache Hadoop (HDFS, MapReduce).

Caching: Redis.

Frontend: HTML5, TailwindCSS, Chart.js.

Mạng / Triển khai: ZeroTier (VPN LAN ảo), SQL Server Linked Server.

🔥 Những gì dự án đã thực hiện được (Key Achievements)
Trong quá trình phát triển, dự án đã triển khai thành công các thành phần kỹ thuật cốt lõi sau:

1. Kiến trúc CSDL Phân tán (Horizontal Database Sharding)

Phân mảnh dữ liệu theo vùng miền: Chia tách dữ liệu giao dịch thành 3 trạm địa lý độc lập (North, Mid, South) dựa trên khóa phân mảnh là Province.

Định tuyến động (Dynamic Routing): Xây dựng cơ chế DynamicDataSource kết hợp ThreadLocal để tự động "bẻ ghi" kết nối CSDL theo luồng giao dịch mà không cần hardcode.

Linked Server & Global View: Cấu hình thành công Linked Server trên SQL Server và tạo các View tổng hợp xuyên trạm, cho phép truy xuất dữ liệu trong suốt (Location Transparency) từ máy chủ Central.

Tối ưu Schema cho phân tán: Gỡ bỏ ràng buộc Foreign Key vật lý cho các giao dịch liên miền, thay thế bằng Native Query để chèn bản sao giao dịch, giải quyết triệt để lỗi xung đột phiên bản (Optimistic Locking/Row was already updated).

2. Giao dịch Phân tán với Saga Pattern (Distributed Transactions)

Giải quyết bài toán "Double Spending" và Deadlock bằng cách áp dụng Saga Pattern (Choreography-based) thông qua Apache Kafka.

Luồng giao dịch bất đồng bộ: Trừ tiền tại Node gửi ➔ Bắn Event chứa thông tin giao dịch lên Topic Kafka ➔ Node nhận Consume Event và tiến hành cộng tiền.

Cơ chế Rollback tự động: Tự động hoàn tiền (Reversal) tại trạm gửi nếu quá trình xử lý tại trạm nhận gặp sự cố, đảm bảo tính nguyên tử (Atomicity) cho giao dịch xuyên miền.

3. Tích hợp Big Data & Xử lý MapReduce (OLAP)

Đồng bộ thời gian thực: Xây dựng Consumer riêng biệt để hút dữ liệu giao dịch đã hoàn tất từ Kafka và lưu trữ trực tiếp xuống cụm Hadoop HDFS.

Chạy MapReduce Job: Ứng dụng MapReduce để xử lý các bài toán phân tích dữ liệu lớn mà CSDL quan hệ thông thường không đáp ứng được:

Cảnh báo rủi ro theo khu vực (District Risk): Tính toán tỷ lệ giao dịch thất bại (FAILED/REVERSED), phát hiện các mẫu rủi ro (Rapid Fire, Fan-out, Big Amount) theo từng quý/năm.

Mật độ giao dịch: Phân tích và xếp hạng tổng khối lượng giao dịch gộp từ các mảnh (Province Density).

4. Giao diện Quản trị & Trực quan hóa dữ liệu (Admin Dashboard)

Xây dựng giao diện Dashboard chuyên nghiệp với TailwindCSS.

Tích hợp Chart.js để vẽ biểu đồ thống kê trực quan (Biểu đồ số lượng giao dịch, dòng tiền vào/ra, cảnh báo rủi ro).

Xử lý giao diện động (SPA-like) với hệ thống Tab, phân trang và bộ lọc tra cứu giao dịch đa chiều.

5. Triển khai Hạ tầng Vật lý

Thiết lập mạng LAN ảo (Virtual LAN) bằng ZeroTier, cấp phát IP tĩnh để kết nối máy tính của các thành viên trong nhóm thành một cụm mạng nội bộ.

Mở Port 1433, cấu hình TCP/IP trên SQL Server Configuration Manager cho phép kết nối chéo giữa các máy trạm thực tế.

⚙️ Hướng dẫn cài đặt (Installation)
1. Clone dự án

Bash
git clone https://github.com/Hunggoodboy/WebBanking.git
cd WebBanking
2. Khởi chạy Middleware

Yêu cầu hệ thống phải đang chạy Redis, Apache Kafka (Zookeeper & Kafka Server), và Hadoop HDFS.

3. Cấu hình Database

Khôi phục 4 database (BankingEconomy, WebBanking_North, WebBanking_Mid, WebBanking_South) lên các SQL Server instance tương ứng.

Đảm bảo đã chạy lệnh DROP CONSTRAINT cho khóa ngoại from_account_id trên bảng transactions tại 3 trạm địa phương.

Cập nhật IP ZeroTier vào file application.properties.

4. Chạy ứng dụng

Bash
mvn spring-boot:run
📈 Định hướng phát triển tương lai (Future Works)
Chuyển đổi hoàn toàn kiến trúc sang Microservices (Tách biệt Identity Service, Transfer Service, và Report Service).

Triển khai hệ thống lên nền tảng Cloud (AWS/GCP) kết hợp Docker/Kubernetes để tự động mở rộng (Auto-scaling).

Tích hợp Machine Learning để tự động phát hiện gian lận (Fraud Detection) dựa trên tập dữ liệu đã lưu trên HDFS.