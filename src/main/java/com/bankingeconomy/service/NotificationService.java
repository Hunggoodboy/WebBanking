package com.bankingeconomy.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.bankingeconomy.entity.Notification;

public interface NotificationService {
    void sendNotification(String userId, String message, Notification.NotificationType type);
    Page<Notification> getByUser(UUID userId, Pageable pageable);
    void markAsRead(UUID notificationId);
}
