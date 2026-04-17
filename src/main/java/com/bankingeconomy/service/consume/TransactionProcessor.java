package com.bankingeconomy.service.consumer;

import com.bankingeconomy.event.TransferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private final TransactionProcessorService transactionProcessorService;

    @KafkaListener(topics = "transfer-topic", groupId = "transaction-group")
    public void consume(TransferEvent event) {
        log.info("Received Kafka event: txId={} status={}",
                event.getTransactionId(), event.getStatus());

        try {
            transactionProcessorService.processTransaction(event);
        } catch (Exception e) {
            log.error("Unhandled error: txId={} error={}",
                    event.getTransactionId(), e.getMessage(), e);
        }
    }
}