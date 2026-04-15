package com.bankingeconomy.service;

import com.bankingeconomy.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditHdfsService {

    private final FileSystem fileSystem;

    private static final String AUDIT_BASE_PATH = "/banking/audit/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Ghi một AuditLog lên HDFS dưới dạng CSV (hỗ trợ append đúng cách)
     */
    public void writeAuditToHDFS(AuditLog auditLog) throws IOException {
        String today = java.time.LocalDate.now().format(DATE_FORMATTER);
        Path filePath = new Path(AUDIT_BASE_PATH + "audit_" + today + ".csv");
        Path dirPath = new Path(AUDIT_BASE_PATH);

        // Tạo thư mục nếu chưa tồn tại
        if (!fileSystem.exists(dirPath)) {
            fileSystem.mkdirs(dirPath);
        }

        boolean fileExists = fileSystem.exists(filePath);
        String header = "transaction_id,from_account,to_account,amount,status,description,fail_reason,processed_at,created_at\n";

        FSDataOutputStream out = null;

        try {
            if (!fileExists) {
                // Tạo file mới và ghi header
                out = fileSystem.create(filePath, false); // false = không overwrite
                out.write(header.getBytes(StandardCharsets.UTF_8));
                log.info("📁 Tạo file audit mới trên HDFS: {}", filePath);
            } else {
                // Append vào file đã tồn tại
                out = fileSystem.append(filePath);
            }

            // Ghi dòng dữ liệu audit
            String line = String.format("%s,%s,%s,%.2f,%s,%s,%s,%s,%s\n",
                    auditLog.getTransactionId() != null ? auditLog.getTransactionId() : "",
                    auditLog.getFromAccountNumber() != null ? auditLog.getFromAccountNumber() : "",
                    auditLog.getToAccountNumber() != null ? auditLog.getToAccountNumber() : "",
                    auditLog.getAmount() != null ? auditLog.getAmount() : 0.0,
                    auditLog.getStatus() != null ? auditLog.getStatus() : "UNKNOWN",
                    auditLog.getDescription() != null ? auditLog.getDescription().replace(",", " ") : "",
                    auditLog.getFailReason() != null ? auditLog.getFailReason().replace(",", " ") : "",
                    auditLog.getProcessedAt() != null ? auditLog.getProcessedAt() : "",
                    auditLog.getCreatedAt() != null ? auditLog.getCreatedAt() : ""
            );

            out.write(line.getBytes(StandardCharsets.UTF_8));
            log.info("✅ Đã ghi audit thành công lên HDFS: {}", filePath);

        } catch (IOException e) {
            log.error("❌ Lỗi khi ghi audit lên HDFS: {}", e.getMessage(), e);
            throw e;
        } finally {
            // Đảm bảo đóng stream
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.warn("⚠️ Lỗi khi đóng FSDataOutputStream: {}", e.getMessage());
                }
            }
        }
    }
}