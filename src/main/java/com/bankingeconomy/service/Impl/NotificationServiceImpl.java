package com.bankingeconomy.service.Impl;

import java.util.Date;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;

import com.bankingeconomy.entity.Notification;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.repository.NotificationRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.NotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate; // 👈 thêm mới

    @Override
    public void sendNotification(String userId, String message, Notification.NotificationType type) {
        User user = userRepository.findById(UUID.fromString(userId)).orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng để gửi thông báo"));

        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(new Date())
                .build();

        // 1. Lưu vào DB
        notificationRepository.save(notification);
        log.info("✅ Đã lưu notification cho user {}: {}", userId, message);

        // 2. Push real-time qua WebSocket 
        messagingTemplate.convertAndSend("/topic/notification/" + userId, message);
        log.info("📡 Đã push WebSocket tới user {}: {}", userId, message);
    }

    @Override
    public Page<Notification> getByUser(User user, Pageable pageable) {
        UUID userId = user.getId();
        return notificationRepository.findByUserId(userId, pageable);
    }

    @Override
    public void markAsRead(UUID notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
            log.info("✅ Đã đánh dấu đã đọc notification: {}", notificationId);
        });
    }
}
