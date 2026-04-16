package com.bankingeconomy.service.Impl;

import java.util.Date;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Override
    public void sendNotification(String userId, String message, Notification.NotificationType type) {
        User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy user: " + userId));

        Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .type(type)
                .isRead(false)
                .createdAt(new Date())
                .build();

        notificationRepository.save(notification);
        log.info("✅ Đã lưu notification cho user {}: {}", userId, message);
    }

    @Override
    public Page<Notification> getByUser(Long userId, Pageable pageable) {
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