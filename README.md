🏦 Distributed Web Banking System (Hệ thống Ngân hàng Phân tán)
Dự án này là một hệ thống Core Banking phân tán toàn diện, được thiết kế để giải quyết bài toán quản lý giao dịch quy mô lớn, đảm bảo tính sẵn sàng cao và khả năng mở rộng thông qua kiến trúc phân mảnh dữ liệu (Sharding) và xử lý bất đồng bộ.

🏗️ Kiến trúc hệ thống (Architecture)
Hệ thống được xây dựng theo mô hình Hybrid Distributed Architecture:

Central Node (Trung tâm): Quản lý định danh người dùng (Identity Management) toàn hệ thống.

Regional Nodes (Máy trạm địa phương): Gồm 3 phân mảnh (North, Mid, South) lưu trữ số dư tài khoản và lịch sử giao dịch theo khu vực địa lý.

Big Data Layer (Hadoop HDFS): Lưu trữ và phân tích dữ liệu lịch sử giao dịch khổng lồ phục vụ mục đích OLAP.

🚀 Công nghệ sử dụng (Tech Stack)
Backend: Java 17, Spring Boot 3, Spring Data JPA.

Database: 04 Node SQL Server (1 Central + 3 Regional).

Messaging Queue: Apache Kafka (Xử lý giao dịch liên miền).

Caching: Redis (Quản lý số dư thời gian thực).

Big Data: Hadoop HDFS (Lưu trữ lịch sử giao dịch).

Networking: ZeroTier/Radmin VPN (Kết nối mạng LAN ảo giữa các máy chủ vật lý).

🛠️ Các tính năng cốt lõi
1. Phân mảnh dữ liệu thông minh (Horizontal Sharding)

Hệ thống tự động điều hướng kết nối (Dynamic Data Routing) dựa trên tỉnh/thành phố của người dùng.

Central DB: Lưu bảng Users.

Regional DB: Lưu bảng Accounts và Transactions.

2. Giao dịch phân tán (Distributed Transactions)

Sử dụng Saga Pattern (Choreography-based) thông qua Kafka để đảm bảo tính nhất quán dữ liệu giữa hai máy trạm khác nhau.

Cơ chế Rollback tự động: Hoàn tiền cho tài khoản gửi nếu quá trình cộng tiền tại tài khoản nhận gặp sự cố.

3. Tích hợp Big Data

Mỗi giao dịch thành công tại máy trạm sẽ được đồng bộ hóa tức thời xuống cụm Hadoop HDFS dưới định dạng file Parquet/CSV để phục vụ thống kê báo cáo.

💻 Cấu hình & Cài đặt
1. Kết nối mạng vật lý

Cài đặt ZeroTier trên tất cả các máy trạm.

Cấu hình IP Managed ZeroTier vào file application.properties thay cho localhost.

2. Cấu hình SQL Server

Kích hoạt TCP/IP và Port 1433 trên tất cả các Node.

Thiết lập Linked Server giữa Central và các Station để phục vụ truy vấn báo cáo tổng hợp.

Gỡ bỏ Foreign Key trên cột from_account_id tại các trạm để hỗ trợ lưu trữ dữ liệu liên miền.

3. Biến môi trường (Application Properties)

Properties
# Ví dụ cấu hình điều hướng DB
spring.datasource.central.jdbc-url=jdbc:sqlserver://[IP_CENTRAL]:1433;databaseName=BankingEconomy
spring.datasource.north.jdbc-url=jdbc:sqlserver://[IP_NORTH]:1433;databaseName=WebBanking_North
# ... (tương tự cho MID và SOUTH)
📊 Luồng hoạt động chính (Workflow)
Đăng ký: User đăng ký tại Central -> Hệ thống dựa vào tỉnh/thành để tạo tài khoản mặc định tại DB trạm tương ứng.

Chuyển tiền:

Trừ tiền tại DB nguồn (Miền gửi).

Bắn sự kiện sang Kafka.

Consumer nhận sự kiện -> Cộng tiền tại DB đích (Miền nhận).

Đẩy lịch sử giao dịch vào HDFS.

📝 Kết luận
Dự án minh chứng cho khả năng xây dựng một hệ thống tài chính phân tán có khả năng chịu lỗi cao, xử lý dữ liệu lớn và đảm bảo tính toàn vẹn của giao dịch trong môi trường mạng không ổn định.

Author: Nguyễn Xuân Hùng
Subject: Cơ sở dữ liệu phân tán - Post and Telecommunications Institute of Technology.