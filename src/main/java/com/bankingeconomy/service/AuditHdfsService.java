package com.bankingeconomy.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.stereotype.Service;

import com.bankingeconomy.entity.AuditLog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditHdfsService {

    private final FileSystem fileSystem;   // Đã inject từ HadoopConfig

    private static final String AUDIT_BASE_PATH = "/banking/audit/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Ghi một AuditLog lên HDFS dưới dạng CSV
     */
    public void writeAuditToHDFS(AuditLog auditLog) throws IOException {
        String today = java.time.LocalDate.now().format(DATE_FORMATTER);
        Path filePath = new Path(AUDIT_BASE_PATH + "audit_" + today + ".csv");

        // Tạo thư mục nếu chưa tồn tại
        Path dirPath = new Path(AUDIT_BASE_PATH);
        if (!fileSystem.exists(dirPath)) {
            fileSystem.mkdirs(dirPath);
        }

        boolean fileExists = fileSystem.exists(filePath);
        String header = "transaction_id,from_account,to_account,amount,status,description,fail_reason,processed_at,created_at\n";

        try (FSDataOutputStream out = fileSystem.create(filePath, true)) {  // true = append nếu file tồn tại
            // Ghi header nếu file chưa tồn tại
            if (!fileExists) {
                out.write(header.getBytes(StandardCharsets.UTF_8));
            }

            // Ghi dữ liệu audit
            String line = String.format("%s,%s,%s,%.2f,%s,%s,%s,%s,%s\n",
                    auditLog.getTransactionId(),
                    auditLog.getFromAccountNumber() != null ? auditLog.getFromAccountNumber() : "",
                    auditLog.getToAccountNumber() != null ? auditLog.getToAccountNumber() : "",
                    auditLog.getAmount(),
                    auditLog.getStatus(),
                    auditLog.getDescription() != null ? auditLog.getDescription().replace(",", " ") : "", // tránh lỗi CSV
                    auditLog.getFailReason() != null ? auditLog.getFailReason().replace(",", " ") : "",
                    auditLog.getProcessedAt() != null ? auditLog.getProcessedAt() : "",
                    auditLog.getCreatedAt()
            );

            out.write(line.getBytes(StandardCharsets.UTF_8));
            log.info("✅ Đã ghi audit lên HDFS: {}", filePath);
        }
    }
}