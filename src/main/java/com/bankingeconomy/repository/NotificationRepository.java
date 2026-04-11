package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndType(Long userId, Notification.NotificationType type, Pageable pageable);
}
