package com.bankingeconomy.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.bankingeconomy.entity.Notification;
import com.bankingeconomy.event.TransferEvent;
import com.bankingeconomy.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "transfer-topic", groupId = "notification-group")
    public void handleTransferEvent(String rawMessage) {
        log.info("📥 Nhận message từ Kafka: {}", rawMessage);
        try {
            // Fix: nếu message bị wrap thêm dấu " bên ngoài thì bỏ đi
            String json = rawMessage;
            if (rawMessage.startsWith("\"") && rawMessage.endsWith("\"")) {
                json = objectMapper.readValue(rawMessage, String.class);
            }
            TransferEvent event = objectMapper.readValue(json, TransferEvent.class);
            handleByStatus(event);
        } catch (Exception e) {
            log.error("❌ Lỗi xử lý Kafka message: {}", e.getMessage());
        }
    }

    private void handleByStatus(TransferEvent event) {

        // Kiểm tra null — phòng trường hợp thiếu userId
        if (event.getSenderUserId() == null || event.getSenderUserId().isBlank()
                || event.getReceiverUserId() == null || event.getReceiverUserId().isBlank()) {
            log.warn("⚠️ TransferEvent thiếu senderUserId hoặc receiverUserId, bỏ qua notification. eventId={}", event.getEventId());
            return;
        }

        String amountStr = String.format("%,.0f", event.getAmount()) + " VND";

        switch (event.getStatus()) {
            case COMPLETED -> {
                // Thông báo cho người GỬI
                notificationService.sendNotification(
                        event.getSenderUserId(),
                        "Chuyển tiền " + amountStr + " thành công. Nội dung: " + event.getDescription(),
                        Notification.NotificationType.TRANSACTION
                );
                // Thông báo cho người NHẬN
                notificationService.sendNotification(
                        event.getReceiverUserId(),
                        "Bạn vừa nhận được " + amountStr + ". Nội dung: " + event.getDescription(),
                        Notification.NotificationType.TRANSACTION
                );
            }
            case FAILED -> {
                // Chỉ báo người gửi khi thất bại
                notificationService.sendNotification(
                        event.getSenderUserId(),
                        "Chuyển tiền " + amountStr + " thất bại. Vui lòng thử lại.",
                        Notification.NotificationType.ALERT
                );
            }
            case REVERSED -> {
                notificationService.sendNotification(
                        event.getSenderUserId(),
                        "Giao dịch " + amountStr + " đã bị hoàn tiền.",
                        Notification.NotificationType.TRANSACTION
                );
            }
            default -> log.info("⏳ Trạng thái {} chưa cần gửi notification", event.getStatus());
        }
    }
}