package com.bankingeconomy.producer;

import com.bankingeconomy.dto.event.TransactionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private final KafkaTemplate<String, TransactionEvent> kafkaTemplate;
    private static final String TOPIC = "transaction-events";

    public void publish(TransactionEvent event) {
        kafkaTemplate.send(TOPIC, event.getTransactionId().toString(), event);
    }
}
