package com.bankingeconomy.service.kafka.consumer;

import com.bankingeconomy.dto.HdfsTransactionDTO;
import com.bankingeconomy.dto.event.TransferEvent;
import com.bankingeconomy.service.Impl.TransactionProcessorService;
import com.bankingeconomy.service.kafka.producer.HdfsKafkaProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private final TransactionProcessorService transactionProcessorService;
    private final HdfsKafkaProducer  hdfsKafkaProducer;

    @KafkaListener(topics = "transfer-topic", groupId = "transaction-group")
    public void consume(TransferEvent event) {
        log.info("Received Kafka event: txId={} status={}",
                event.getTransactionId(), event.getStatus());
        log.info("Send to Kafka Producer for write to HDFS: txId={}", event.getTransactionId());
        try {
            transactionProcessorService.processTransaction(event);
            HdfsTransactionDTO hdfsTransactionDTO = HdfsTransactionDTO.convertFromTransferEvent(event);
            hdfsKafkaProducer.pushBatchToKafka(Collections.singletonList(hdfsTransactionDTO));
        } catch (Exception e) {
            log.error("Unhandled error: txId={} error={}",
                    event.getTransactionId(), e.getMessage(), e);
        }
    }
}