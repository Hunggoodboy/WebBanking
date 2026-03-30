package com.bankingeconomy.producer;

import com.bankingeconomy.dto.event.TransactionResultEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionResultPublisher {

    private final KafkaTemplate<String, TransactionResultEvent> kafkaTemplate;
    private static final String TOPIC = "transaction-result";

    public void publishSuccess(TransactionResultEvent event) {
        kafkaTemplate.send(TOPIC, event.getTransactionId().toString(), event);
    }

    public void publishFailed(TransactionResultEvent event) {
        kafkaTemplate.send(TOPIC, event.getTransactionId().toString(), event);
    }
}