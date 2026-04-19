package com.bankingeconomy.service.kafka.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.bankingeconomy.dto.event.TransactionResultEvent;
import com.bankingeconomy.entity.Notification;
import com.bankingeconomy.service.AuditService;
import com.bankingeconomy.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditAndNotificationConsumer {

    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    /**
     * Nhận kết quả xử lý giao dịch từ Nguyễn Đại Cương
     */
    @KafkaListener(topics = "transaction-result", groupId = "kiens-audit-notification-group")
    public void consumeTransactionResult(String rawMessage) {
        log.info("📥 [Kiên] Nhận được TransactionResultEvent từ Kafka: {}", rawMessage);

        try {
            // Xử lý trường hợp message bị wrap trong dấu ngoặc kép
            String json = rawMessage;
            if (rawMessage.startsWith("\"") && rawMessage.endsWith("\"")) {
                json = objectMapper.readValue(rawMessage, String.class);
            }

            TransactionResultEvent event = objectMapper.readValue(json, TransactionResultEvent.class);

            log.info("✅ Parse thành công - TransactionId: {} | Status: {}", 
                    event.getTransactionId(), event.getStatus());

            // 1. Ghi Audit + HDFS (Công việc chính của bạn)
            auditService.logAudit(event);

            // 2. Gửi thông báo cho người dùng
            sendNotifications(event);

        } catch (Exception e) {
            log.error("❌ [Kiên] Lỗi xử lý message Kafka: {}", e.getMessage(), e);
        }
    }

    /**
     * Gửi thông báo tùy theo trạng thái giao dịch
     */
    private void sendNotifications(TransactionResultEvent event) {
        String amountStr = event.getAmount() != null 
                ? String.format("%,.0f VND", event.getAmount()) 
                : "0 VND";

        switch (event.getStatus()) {
            case "COMPLETED" -> {
                notificationService.sendNotification(
                        String.valueOf(event.getFromUserId()),
                        "Chuyển tiền " + amountStr + " thành công. " + (event.getDescription() != null ? event.getDescription() : ""),
                        Notification.NotificationType.TRANSACTION
                );

                notificationService.sendNotification(
                        String.valueOf(event.getToUserId()),
                        "Bạn vừa nhận được " + amountStr + ". " + (event.getDescription() != null ? event.getDescription() : ""),
                        Notification.NotificationType.TRANSACTION
                );
            }

            case "FAILED" -> {
                notificationService.sendNotification(
                        String.valueOf(event.getFromUserId()),
                        "Chuyển tiền " + amountStr + " thất bại. Lý do: " + (event.getFailReason() != null ? event.getFailReason() : "Không rõ"),
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

            default -> 
                log.info("⏳ Trạng thái {} chưa cần gửi thông báo", event.getStatus());
        }
    }
}