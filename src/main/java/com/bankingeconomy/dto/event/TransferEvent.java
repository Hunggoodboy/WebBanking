package com.bankingeconomy.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferEvent {

    private String eventId;
    private String transactionId;

    private String fromAccountId;
    private String toAccountId;
    private String fromAccountNumber;
    private String toAccountNumber;

    // userId để NotificationConsumer gửi thông báo đúng người
    private String senderUserId;
    private String receiverUserId;

    private BigDecimal amount;
    private String currency;
    private String description;

    private TransferStatus status;

    private Instant timestamp;

    public enum TransferStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED
    }
}