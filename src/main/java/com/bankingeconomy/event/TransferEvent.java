package com.bankingeconomy.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent {

    private String eventId;
    private String fromAccountId;
    private String toAccountId;
    private String senderUserId;
    private String receiverUserId;
    private BigDecimal amount;
    private String currency;
    private Instant timestamp;
    private TransferStatus status;
    private String description;
    public enum TransferStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        REVERSED
    }

    /**
     * Factory method tạo event chuẩn
     */
    public static TransferEvent of(
            String fromAccountId,
            String toAccountId,
            String senderUserId,
            String receiverUserId,
            BigDecimal amount
    ) {

        validate(fromAccountId, toAccountId, senderUserId, receiverUserId, amount);

        return TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .senderUserId(senderUserId)
                .receiverUserId(receiverUserId)
                .amount(amount)
                .currency("VND")
                .timestamp(Instant.now())
                .status(TransferStatus.PENDING)
                .description("Transfer from " + fromAccountId + " to " + toAccountId)
                .build();
    }

    /**
     * Validate dữ liệu đầu vào
     */
    private static void validate(
            String fromAccountId,
            String toAccountId,
            String senderUserId,
            String receiverUserId,
            BigDecimal amount
    ) {
        Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
        Objects.requireNonNull(toAccountId, "toAccountId must not be null");
        Objects.requireNonNull(senderUserId, "senderUserId must not be null");
        Objects.requireNonNull(receiverUserId, "receiverUserId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        if (senderUserId.equals(receiverUserId)) {
            throw new IllegalArgumentException("Sender and receiver cannot be the same user");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0");
        }
    }
}
