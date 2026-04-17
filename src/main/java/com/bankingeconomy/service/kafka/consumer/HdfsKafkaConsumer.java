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
    public void consumeBatch(List<HdfsTransactionDTO> transactions) {
        log.info("Kafka nhận lô giao dịch từ HDFS: {} giao dịch", transactions.size());
        try{
            Map<String, List<String> > partitionedCsvData = processBatchForHdfs(transactions);
            hdfsReadWriteService.writeTransactionsToHDFS(partitionedCsvData);
            log.info("Đã đẩy lô {} giao dịch xuống HDFS thành công.", transactions.size());
        }
        catch (Exception e){
            log.error("Lỗi khi xử lý batch giao dịch cho HDFS: {}", e.getMessage(), e);
        }
    }

    private Map<String, List<String>> processBatchForHdfs(List<HdfsTransactionDTO> transactions) {
        Map<String, List<String>> partitionData = new HashMap<>();
        String BASE_PATH = "/data/transactions/";
        for (HdfsTransactionDTO tx : transactions) {
            int year = tx.getCreatedAt().getYear();
            int quarter = (tx.getCreatedAt().getMonthValue() - 1) / 3 + 1;
            String monthStr = tx.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM"));

            // --- Phía OUT (Người gửi) ---
            String outProv = (tx.getFromProvince() != null && !tx.getFromProvince().isBlank()) ? tx.getFromProvince() : "UNKNOWN";
            String outDist = (tx.getFromDistrict() != null && !tx.getFromDistrict().isBlank()) ? tx.getFromDistrict() : "UNKNOWN";

            String outKey = BASE_PATH + String.format("province=%s/district=%s/year=%d/quarter=Q%d/", outProv, outDist, year, quarter);
            String outCsv = BASE_PATH + String.format("%s,%s,%s,OUT,%s,%.2f,%s,%s,%s,%s,%d,Q%d,%s\n",
                    tx.getTransactionId(), tx.getFromUserId(), tx.getFromAccountNumber(),
                    tx.getToAccountNumber(), tx.getAmount(), tx.getStatus(), tx.getCreatedAt(),
                    outProv, outDist, year, quarter, monthStr);

            partitionData.computeIfAbsent(outKey, k -> new ArrayList<>()).add(outCsv);

            // --- Phía IN (Người nhận) ---
            String inProv = (tx.getToProvince() != null && !tx.getToProvince().isBlank()) ? tx.getToProvince() : "UNKNOWN";
            String inDist = (tx.getToDistrict() != null && !tx.getToDistrict().isBlank()) ? tx.getToDistrict() : "UNKNOWN";

            String inKey = String.format("province=%s/district=%s/year=%d/quarter=Q%d/", inProv, inDist, year, quarter);
            String inCsv = String.format("%s,%s,%s,IN,%s,%.2f,%s,%s,%s,%s,%d,Q%d,%s\n",
                    tx.getTransactionId(), tx.getToUserId(), tx.getToAccountNumber(),
                    tx.getFromAccountNumber(), tx.getAmount(), tx.getStatus(), tx.getCreatedAt(),
                    inProv, inDist, year, quarter, monthStr);

            partitionData.computeIfAbsent(inKey, k -> new ArrayList<>()).add(inCsv);
        }
        return partitionData;
    }


}
