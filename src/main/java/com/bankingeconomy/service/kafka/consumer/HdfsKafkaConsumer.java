package com.bankingeconomy.service.kafka.consumer;

import com.bankingeconomy.dto.HdfsTransactionDTO;
import com.bankingeconomy.service.Impl.HDFSReadWriteServiceImpl;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class HdfsKafkaConsumer {

    private final HDFSReadWriteServiceImpl  hdfsReadWriteService;

    @KafkaListener(topics = "banking.hdfs.sync", groupId = "hdfs-writer-group")
    public void consumeBatch(List<HdfsTransactionDTO> transactions) throws IOException {
        log.info("Kafka nhận lô giao dịch từ HDFS: {} giao dịch", transactions.size());
        Map<String, List<String> > partitionedCsvData = processBatchForHdfs(transactions);
        hdfsReadWriteService.writeTransactionsToHDFS(partitionedCsvData);
        log.info("Đã đẩy lô {} giao dịch xuống HDFS thành công.", transactions.size());
    }

    private Map<String, List<String>> processBatchForHdfs(List<HdfsTransactionDTO> transactions) {
        Map<String, List<String>> partitionData = new HashMap<>();
        String BASE_PATH = "/data/transactions/";
        for (HdfsTransactionDTO tx : transactions) {
            int year = tx.getCreatedAt().getYear();
            int quarter = (tx.getCreatedAt().getMonthValue() - 1) / 3 + 1;
            String monthStr = tx.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));

            // --- Phía OUT (Người gửi) ---
            String outProv = normalizeLocationName(tx.getFromProvince());
            String outDist = normalizeLocationName(tx.getFromDistrict());

            String outKey = BASE_PATH + String.format("province=%s/district=%s/year=%d/quarter=Q%d/", outProv, outDist, year, quarter);
            String outCsv = String.format("%s,%s,%s,OUT,%s,%.2f,%s,%s,%s,%s,%d,Q%d,%s\n",
                    tx.getTransactionId(), tx.getFromUserId(), tx.getFromAccountNumber(),
                    tx.getToAccountNumber(), tx.getAmount(), tx.getStatus(), tx.getCreatedAt(),
                    outProv, outDist, year, quarter, monthStr);

            partitionData.computeIfAbsent(outKey, k -> new ArrayList<>()).add(outCsv);

            // --- Phía IN (Người nhận) ---
            String inProv = normalizeLocationName(tx.getToProvince());
            String inDist = normalizeLocationName(tx.getToDistrict());

            String inKey = BASE_PATH + String.format("province=%s/district=%s/year=%d/quarter=Q%d/", inProv, inDist, year, quarter);
            String inCsv = String.format("%s,%s,%s,IN,%s,%.2f,%s,%s,%s,%s,%d,Q%d,%s\n",
                    tx.getTransactionId(), tx.getToUserId(), tx.getToAccountNumber(),
                    tx.getFromAccountNumber(), tx.getAmount(), tx.getStatus(), tx.getCreatedAt(),
                    inProv, inDist, year, quarter, monthStr);

            partitionData.computeIfAbsent(inKey, k -> new ArrayList<>()).add(inCsv);
        }
        return partitionData;
    }
    /**
     * Hàm chuẩn hóa tên địa danh: "Hà Nội" -> "HaNoi", "thừa thiên huế" -> "ThuaThienHue"
     */
    private String normalizeLocationName(String location) {
        if (location == null || location.trim().isEmpty()) {
            return "Unknown";
        }
        try {
            // 1. Chuẩn hóa NFD để tách các ký tự dấu ra khỏi chữ cái gốc
            String temp = java.text.Normalizer.normalize(location.trim(), java.text.Normalizer.Form.NFD);

            // 2. Dùng Regex quét và xóa sạch các ký tự dấu vừa tách
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
            String noAccent = pattern.matcher(temp).replaceAll("");

            // 3. Xử lý trường hợp ngoại lệ: Chữ Đ/đ
            noAccent = noAccent.replace("Đ", "D").replace("đ", "d");

            // 4. Loại bỏ các ký tự đặc biệt, chỉ giữ lại chữ cái và khoảng trắng
            noAccent = noAccent.replaceAll("[^a-zA-Z\\s]", "");

            // 5. Cắt từ, viết hoa chữ cái đầu và ghép liền nhau (PascalCase)
            String[] words = noAccent.split("\\s+");
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    // Chữ cái đầu viết hoa, phần còn lại viết thường
                    result.append(word.substring(0, 1).toUpperCase());
                    result.append(word.substring(1).toLowerCase());
                }
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Lỗi khi chuẩn hóa địa danh: {}", location, e);
            return "Unknown";
        }
    }

}
