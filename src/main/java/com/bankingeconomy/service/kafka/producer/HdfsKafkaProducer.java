package com.bankingeconomy.service.kafka.producer;

import com.bankingeconomy.dto.HdfsTransactionDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class HdfsKafkaProducer {
    private final KafkaTemplate<String, HdfsTransactionDTO> kafkaTemplate;

    public void pushBatchToKafka(List<HdfsTransactionDTO> transactions) {
        log.info("Bơm vào kafka " , transactions.size() + " giao dịch");
        for (HdfsTransactionDTO transaction : transactions) {
            kafkaTemplate.send("banking.hdfs.sync",transaction.getTransactionId().toString(), transaction);
        }
    }
}
