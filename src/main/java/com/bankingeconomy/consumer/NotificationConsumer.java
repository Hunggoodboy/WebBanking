package com.bankingeconomy.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.bankingeconomy.dto.event.TransactionResultEvent;
import com.bankingeconomy.entity.Notification;
import com.bankingeconomy.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transaction-result", groupId = "notification-group")
    public void consume(String rawMessage) {
        log.info("📥 [NotificationConsumer] Nhận message từ Kafka: {}", rawMessage);

        try {
            // Xử lý trường hợp message bị wrap trong dấu ngoặc kép
            String json = rawMessage;
            if (rawMessage.startsWith("\"") && rawMessage.endsWith("\"")) {
                json = objectMapper.readValue(rawMessage, String.class);
            }

            TransactionResultEvent event = objectMapper.readValue(json, TransactionResultEvent.class);

            String amountStr = event.getAmount() != null
                    ? String.format("%,.0f VND", event.getAmount())
                    : "0 VND";

            switch (event.getStatus()) {
                case "COMPLETED" -> {
                    // Thông báo cho người gửi
                    notificationService.sendNotification(
                            String.valueOf(event.getFromUserId()),
                            "Chuyển " + amountStr + " thành công. " +
                            (event.getDescription() != null ? event.getDescription() : ""),
                            Notification.NotificationType.TRANSACTION
                    );

                    // Thông báo cho người nhận 👈 real-time hiện lên màn hình
                    notificationService.sendNotification(
                            String.valueOf(event.getToUserId()),
                            "Bạn vừa nhận được " + amountStr + ". " +
                            (event.getDescription() != null ? event.getDescription() : ""),
                            Notification.NotificationType.TRANSACTION
                    );
                }

                case "FAILED" -> {
                    notificationService.sendNotification(
                            String.valueOf(event.getFromUserId()),
                            "Chuyển tiền " + amountStr + " thất bại. Lý do: " +
                            (event.getFailReason() != null ? event.getFailReason() : "Không rõ"),
                            Notification.NotificationType.ALERT
                    );
                }

                case "REVERSED" -> {
                    notificationService.sendNotification(
                            String.valueOf(event.getFromUserId()),
                            "Giao dịch " + amountStr + " đã bị hoàn tiền.",
                            Notification.NotificationType.TRANSACTION
                    );
                }

                default -> log.info("⏳ Trạng thái {} chưa cần gửi thông báo", event.getStatus());
            }

        } catch (Exception e) {
            log.error("❌ [NotificationConsumer] Lỗi xử lý message: {}", e.getMessage(), e);
        }
    }
}