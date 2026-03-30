package com.bankingeconomy.consumer;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;

    // Đọc kết quả từ Đại Cương xử lý xong
    @KafkaListener(topics = "transaction-result", groupId = "notification-service")
    public void consume(TransactionResultEvent event) {
        notificationService.sendTransactionNotification(event);
    }
}
