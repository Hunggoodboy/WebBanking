package com.bankingeconomy.controller;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.Notification;
import com.bankingeconomy.service.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Lấy thông báo cũ từ DB (có phân trang)
     * GET /api/notifications/{userId}?page=0&size=10
     */
    @GetMapping("/{userId}")
    public ResponseData<Page<Notification>> getNotifications(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Notification> notifications = notificationService.getByUser(userId, pageable);

        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Lấy danh sách thông báo thành công",
                notifications
        );
    }

    /**
     * Đánh dấu đã đọc
     * PUT /api/notifications/{notificationId}/read
     */
    @PutMapping("/{notificationId}/read")
    public ResponseData<String> markAsRead(@PathVariable UUID notificationId) {
        notificationService.markAsRead(notificationId);
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Đánh dấu đã đọc thành công",
                null
        );
    }
}
