package com.bankingeconomy.service.consumer;


import com.bankingeconomy.event.TransferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private final TransactionProcessorService transactionProcessorService;

    @KafkaListener(topics = "transaction-topic", groupId = "transaction-group")
    public void consume(TransferEvent event) {

        log.info("Received TransferEvent: {}", event);

        try {
            // Generate transactionId (optional tracking)
            UUID transactionId = UUID.randomUUID();
            log.info("Transaction ID: {}", transactionId);

            transactionProcessorService.processTransaction(event);

        } catch (Exception e) {
            log.error("Error processing transaction: {}", e.getMessage(), e);
        }
    }
}
