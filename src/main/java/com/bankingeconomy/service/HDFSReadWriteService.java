package com.bankingeconomy.service;

import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.*;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service đọc/ghi dữ liệu giao dịch lên HDFS theo thiết kế phân mảnh ngang.
 *
 * <p>Khóa phân mảnh vật lý: province, district, year, quarter.
 * <p>Cấu trúc HDFS:
 * <pre>
 *   /data/transactions/province={p}/district={d}/year={y}/quarter={q}/part-xxxxx.csv
 * </pre>
 *
 * <p>Mỗi giao dịch gốc được tách thành 2 bản ghi trong transaction_fact_geo:
 * <ul>
 *   <li>OUT – gắn với province/district của người gửi</li>
 *   <li>IN  – gắn với province/district của người nhận</li>
 * </ul>
 *
 * FileSystem được inject từ HadoopConfig đã có trong project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HDFSReadWriteService {

    private final FileSystem fileSystem;

    /** Đường dẫn gốc trên HDFS cho dữ liệu phân mảnh */
    private static final String BASE_PATH = "/data/transactions/";

    /** Header CSV của transaction_fact_geo */
    private static final String CSV_HEADER =
            "transaction_id,owner_user_id,owner_account_id,direction,"
                    + "counterparty_account_id,amount,status,created_at,"
                    + "province,district,year,quarter,month\n";

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ================================================================
    // GHI – Xuất danh sách giao dịch lên HDFS theo phân mảnh
    // ================================================================

    /**
     * Ghi danh sách giao dịch lên HDFS theo phân mảnh ngang.
     *
     * <p>Tự động xây dựng bản đồ ánh xạ user → [province, district]
     * từ thông tin có trong các entity Transaction → Account → User.
     *
     * @param month        tháng dữ liệu (dùng cho log, ví dụ: "2026_04")
     * @param transactions danh sách giao dịch từ SQL Server
     */
    public void writeTransactions(String month, List<Transaction> transactions) throws IOException {
        // Tự xây dựng userLocationMap từ dữ liệu entity
        Map<Long, String[]> userLocationMap = new HashMap<>();

        for (Transaction tx : transactions) {
            extractUserLocation(tx.getFromAccount(), userLocationMap);
            extractUserLocation(tx.getToAccount(), userLocationMap);
        }

        log.info("Bắt đầu xuất {} giao dịch tháng {} lên HDFS theo phân mảnh...",
                transactions.size(), month);

        writeTransactionsByPartition(transactions, userLocationMap);
    }

    /**
     * Trích xuất thông tin province/district của user từ Account entity
     * và đưa vào userLocationMap.
     */
    private void extractUserLocation(Account account, Map<Long, String[]> locationMap) {
        if (account == null || account.getUser() == null) return;

        User user = account.getUser();
        Long userId = user.getId();

        if (!locationMap.containsKey(userId)) {
            // TODO: Khi có bảng user_location_map, thay thế logic này
            //       bằng truy vấn thực tế từ bảng đó.
            // Hiện tại dùng giá trị mặc định "UNKNOWN" nếu chưa có province/district
            String province = "UNKNOWN";
            String district = "UNKNOWN";

            locationMap.put(userId, new String[]{province, district});
        }
    }

    /**
     * Chuyển đổi danh sách Transaction thành các bản ghi transaction_fact_geo
     * rồi ghi vào đúng mảnh (partition) trên HDFS.
     *
     * <p>Cần truyền thêm bản đồ ánh xạ user → địa bàn (province, district)
     * để xác định khóa phân mảnh không gian.
     *
     * @param transactions     danh sách giao dịch gốc từ SQL Server
     * @param userLocationMap  ánh xạ userId → [province, district]
     */
    public void writeTransactionsByPartition(
            List<Transaction> transactions,
            Map<Long, String[]> userLocationMap) throws IOException {

        // Nhóm các dòng fact_geo theo partition key
        Map<String, List<String>> partitionData = new LinkedHashMap<>();

        for (Transaction tx : transactions) {
            // --- Phía OUT (người gửi) ---
            buildFactGeoLine(tx, tx.getFromAccount(), "OUT",
                    tx.getToAccount(), userLocationMap, partitionData);

            // --- Phía IN (người nhận) ---
            buildFactGeoLine(tx, tx.getToAccount(), "IN",
                    tx.getFromAccount(), userLocationMap, partitionData);
        }

        // Ghi từng partition lên HDFS
        int totalLines = 0;
        for (Map.Entry<String, List<String>> entry : partitionData.entrySet()) {
            String partitionPath = entry.getKey();
            List<String> lines = entry.getValue();

            Path filePath = new Path(partitionPath + "part-00001.csv");
            // Tạo thư mục partition nếu chưa có
            fileSystem.mkdirs(filePath.getParent());

            try (FSDataOutputStream out = fileSystem.create(filePath, true)) {
                out.write(CSV_HEADER.getBytes(StandardCharsets.UTF_8));
                for (String line : lines) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                }
            }

            totalLines += lines.size();
            log.info("Ghi {} dòng vào mảnh HDFS: {}", lines.size(), filePath);
        }

        log.info("Hoàn tất ghi {} dòng transaction_fact_geo vào {} mảnh trên HDFS",
                totalLines, partitionData.size());
    }

    // ================================================================
    // ĐỌC – Đọc dữ liệu từ một mảnh hoặc nhiều mảnh
    // ================================================================

    /**
     * Đọc tất cả dòng CSV trong một mảnh cụ thể.
     *
     * @param province  mã tỉnh (ví dụ: "HN", "HCM")
     * @param district  mã quận/huyện (ví dụ: "CauGiay", "Quan1")
     * @param year      năm (ví dụ: 2026)
     * @param quarter   quý (ví dụ: "Q1")
     * @return danh sách các dòng CSV (không bao gồm header)
     */
    public List<String> readPartition(String province, String district,
                                      int year, String quarter) throws IOException {
        String dir = buildPartitionDir(province, district, year, quarter);
        Path partitionPath = new Path(dir);

        if (!fileSystem.exists(partitionPath)) {
            log.warn("Mảnh không tồn tại trên HDFS: {}", partitionPath);
            return List.of();
        }

        List<String> allLines = new ArrayList<>();

        // Đọc tất cả các file CSV trong thư mục partition
        FileStatus[] files = fileSystem.listStatus(partitionPath,
                (Path p) -> p.getName().endsWith(".csv"));

        for (FileStatus file : files) {
            try (FSDataInputStream in = fileSystem.open(file.getPath());
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(in, StandardCharsets.UTF_8))) {

                String line;
                boolean isHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (isHeader) { isHeader = false; continue; }
                    if (!line.isBlank()) {
                        allLines.add(line);
                    }
                }
            }
        }

        log.info("Đọc {} dòng từ mảnh HDFS: {}", allLines.size(), partitionPath);
        return allLines;
    }

    /**
     * Đọc dữ liệu từ tất cả các mảnh thuộc một province và year/quarter.
     * Tức là quét tất cả district trong province đó.
     *
     * @param province  mã tỉnh
     * @param year      năm
     * @param quarter   quý
     * @return danh sách dòng CSV gộp từ mọi district
     */
    public List<String> readByProvinceAndQuarter(String province, int year,
                                                 String quarter) throws IOException {
        String basePath = BASE_PATH + "province=" + province + "/";
        Path provincePath = new Path(basePath);

        if (!fileSystem.exists(provincePath)) {
            log.warn("Không tồn tại dữ liệu cho province={} trên HDFS", province);
            return List.of();
        }

        List<String> allLines = new ArrayList<>();

        // Duyệt qua tất cả district
        FileStatus[] districts = fileSystem.listStatus(provincePath);
        for (FileStatus districtDir : districts) {
            if (!districtDir.isDirectory()) continue;

            String districtName = districtDir.getPath().getName().replace("district=", "");
            List<String> partitionLines = readPartition(province, districtName, year, quarter);
            allLines.addAll(partitionLines);
        }

        log.info("Đọc tổng {} dòng cho province={}, year={}, quarter={}",
                allLines.size(), province, year, quarter);
        return allLines;
    }

    // ================================================================
    // KIỂM TRA – Kiểm tra mảnh tồn tại trên HDFS
    // ================================================================

    /**
     * Kiểm tra mảnh có tồn tại trên HDFS không.
     */
    public boolean partitionExists(String province, String district,
                                   int year, String quarter) throws IOException {
        Path path = new Path(buildPartitionDir(province, district, year, quarter));
        return fileSystem.exists(path);
    }

    // ================================================================
    // XÓA – Xóa một mảnh (dùng khi cần ghi lại dữ liệu sai)
    // ================================================================

    /**
     * Xóa toàn bộ dữ liệu trong một mảnh.
     */
    public void deletePartition(String province, String district,
                                int year, String quarter) throws IOException {
        Path path = new Path(buildPartitionDir(province, district, year, quarter));
        if (fileSystem.exists(path)) {
            fileSystem.delete(path, true);
            log.info("Đã xóa mảnh HDFS: {}", path);
        }
    }

    /**
     * Liệt kê tất cả các mảnh (partition) hiện có trên HDFS.
     *
     * @return danh sách đường dẫn partition
     */
    public List<String> listAllPartitions() throws IOException {
        List<String> partitions = new ArrayList<>();
        Path base = new Path(BASE_PATH);

        if (!fileSystem.exists(base)) {
            return partitions;
        }

        // Duyệt province → district → year → quarter
        for (FileStatus prov : fileSystem.listStatus(base)) {
            if (!prov.isDirectory()) continue;
            for (FileStatus dist : fileSystem.listStatus(prov.getPath())) {
                if (!dist.isDirectory()) continue;
                for (FileStatus yr : fileSystem.listStatus(dist.getPath())) {
                    if (!yr.isDirectory()) continue;
                    for (FileStatus qtr : fileSystem.listStatus(yr.getPath())) {
                        if (!qtr.isDirectory()) continue;
                        partitions.add(qtr.getPath().toString());
                    }
                }
            }
        }

        return partitions;
    }

    // ================================================================
    // PRIVATE HELPERS
    // ================================================================

    /**
     * Tạo một dòng CSV dạng transaction_fact_geo và thêm vào đúng partition.
     */
    private void buildFactGeoLine(Transaction tx,
                                  Account ownerAccount,
                                  String direction,
                                  Account counterpartyAccount,
                                  Map<Long, String[]> userLocationMap,
                                  Map<String, List<String>> partitionData) {

        if (ownerAccount == null || ownerAccount.getUser() == null) return;

        User ownerUser = ownerAccount.getUser();
        Long ownerUserId = ownerUser.getId();

        // Lấy thông tin địa bàn từ bản đồ ánh xạ
        String[] location = userLocationMap.get(ownerUserId);
        if (location == null || location.length < 2) {
            log.warn("Không tìm thấy location cho userId={}. Bỏ qua.", ownerUserId);
            return;
        }

        String province = location[0];
        String district = location[1];

        LocalDateTime createdAt = tx.getCreatedAt();
        int year = createdAt.getYear();
        String quarter = getQuarter(createdAt.getMonthValue());
        String month = createdAt.format(MONTH_FMT);

        // Build dòng CSV
        String line = String.format("%s,%d,%s,%s,%s,%.2f,%s,%s,%s,%s,%d,%s,%s\n",
                tx.getId(),
                ownerUserId,
                ownerAccount.getAccountNumber(),
                direction,
                counterpartyAccount != null ? counterpartyAccount.getAccountNumber() : "",
                tx.getAmount(),
                tx.getStatus(),
                createdAt,
                province,
                district,
                year,
                quarter,
                month
        );

        // Xác định partition path
        String partitionDir = buildPartitionDir(province, district, year, quarter);
        partitionData.computeIfAbsent(partitionDir, k -> new ArrayList<>()).add(line);
    }

    /**
     * Tạo đường dẫn thư mục partition trên HDFS.
     * Ví dụ: /data/transactions/province=HN/district=CauGiay/year=2026/quarter=Q1/
     */
    private String buildPartitionDir(String province, String district,
                                     int year, String quarter) {
        return String.format("%sprovince=%s/district=%s/year=%d/quarter=%s/",
                BASE_PATH, province, district, year, quarter);
    }

    /**
     * Xác định quý từ tháng.
     */
    private String getQuarter(int month) {
        if (month <= 3) return "Q1";
        if (month <= 6) return "Q2";
        if (month <= 9) return "Q3";
        return "Q4";
    }
}