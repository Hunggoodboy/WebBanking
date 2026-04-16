package com.bankingeconomy.service.Impl;

import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.service.HDFSReadWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Service đọc/ghi dữ liệu giao dịch lên HDFS.
 *
 * FileSystem được inject từ HadoopConfig đã có trong project.
 * Phần chia block, chọn DataNode, replication — HDFS tự xử lý,
 * developer không cần quan tâm.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HDFSReadWriteServiceImpl implements HDFSReadWriteService {

    private final FileSystem fileSystem;

    private static final String BASE_PATH = "/banking/transactions/";

    //Ghi danh sách giao dịch lên HDFS dưới dạng file CSV.
    public void writeTransactionsToHdfs(String month, List<Transaction> transactions) throws IOException {
        //Định nghĩa đường dẫn file CSV trên Hadoop.
        Path path = new Path(BASE_PATH + "transactions_" + month + ".csv");

        try (FSDataOutputStream out = fileSystem.create(path, true)) {
            // Ghi header
            out.write("id,from_account,to_account,amount,status,created_at\n"
                    .getBytes(StandardCharsets.UTF_8));

            // Ghi từng dòng giao dịch
            for (Transaction tx : transactions) {
                String line = String.format("%s,%s,%s,%.2f,%s,%s\n",
                        tx.getId(),
                        tx.getFromAccount() != null ? tx.getFromAccount().getAccountNumber() : "",
                        tx.getToAccount()   != null ? tx.getToAccount().getAccountNumber()   : "",
                        tx.getAmount(),
                        tx.getStatus(),
                        tx.getCreatedAt()
                );
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }

        log.info("Đã ghi {} giao dịch lên HDFS: {}", transactions.size(), path);
    }

    // ----------------------------------------------------------------
    // ĐỌC file CSV giao dịch từ HDFS
    // ----------------------------------------------------------------

    /**
     * Đọc danh sách dòng CSV từ file giao dịch trên HDFS.
     */
    public List<String> readTransactionsFromHdfs(String month) throws IOException {
        Path path = new Path(BASE_PATH + "transactions_" + month + ".csv");

        if (!fileSystem.exists(path)) {
            log.warn("File không tồn tại trên HDFS: {}", path);
            return List.of();
        }

        List<String> lines = new ArrayList<>();

        try (FSDataInputStream in = fileSystem.open(path);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                if (isHeader) { isHeader = false; continue; } // bỏ header
                lines.add(line);
            }
        }

        log.info("Đọc {} dòng từ HDFS: {}", lines.size(), path);
        return lines;
    }

    // ----------------------------------------------------------------
    // Kiểm tra file tồn tại
    // ----------------------------------------------------------------

    public boolean exists(String month) throws IOException {
        return fileSystem.exists(new Path(BASE_PATH + "transactions_" + month + ".csv"));
    }

    // ----------------------------------------------------------------
    // Xóa file (dùng khi cần ghi lại)
    // ----------------------------------------------------------------

    public void delete(String month) throws IOException {
        Path path = new Path(BASE_PATH + "transactions_" + month + ".csv");
        if (fileSystem.exists(path)) {
            fileSystem.delete(path, false);
            log.info("Đã xóa file HDFS: {}", path);
        }
    }

}