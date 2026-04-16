package com.bankingeconomy.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// TransactionResultEvent.java
// Đặt trong package: event hoặc dto/event
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionResultEvent {

    private UUID transactionId;

    private UUID fromAccountId;
    private UUID toAccountId;

    // Lấy từ TransactionEvent gốc để Kiên và Hào biết gửi thông báo cho ai
    private UUID fromUserId;
    private UUID toUserId;
    private String fromUserEmail;

    private BigDecimal amount;
    private String description;

    // SUCCESS hoặc FAILED
    private String status;

    // Nếu FAILED thì lý do là gì — Kiên dùng để ghi nội dung thông báo
    private String failReason;

    private LocalDateTime processedAt;
}