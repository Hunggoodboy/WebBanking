package com.bankingeconomy.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditConsumer {

    private final AuditService auditService;

    // Cùng consume transaction-result như Kiên nhưng group khác
    @KafkaListener(topics = "transaction-result", groupId = "audit-service")
    public void consume(TransactionResultEvent event) {
        auditService.logAndStore(event);
    }
}