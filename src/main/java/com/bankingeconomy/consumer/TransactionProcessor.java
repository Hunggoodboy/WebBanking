package com.bankingeconomy.consumer;

import com.bankingeconomy.dto.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionProcessor {

    private final TransactionProcessorService processorService;

    // Consume từ Kafka topic mà Quân publish
    @KafkaListener(topics = "transaction-events", groupId = "transaction-processor")
    public void process(TransactionEvent event) {
        processorService.processTransaction(event);
    }
}
