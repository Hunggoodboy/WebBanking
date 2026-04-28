package com.bankingeconomy.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Event đại diện cho một yêu cầu chuyển tiền.
 * Vị trí: com.bankingeconomy.dto.event.TransferEvent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent {

    // ID duy nhất của sự kiện (dùng để tracking log)
    private String eventId;

    // ID của Transaction trong Database (dùng làm Idempotency Key)
    private String transactionId;

    // Thông tin tài khoản nguồn
    private String fromAccountId;
    private String fromAccountNumber;
    private String senderUserId;
    private String fromProvince;
    private String fromDistrict;

    // Thông tin tài khoản đích
    private String toAccountId;
    private String toAccountNumber;
    private String receiverUserId;
    private String toProvince;
    private String toDistrict;

    // Chi tiết giao dịch
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

    /**
     * Factory method để khởi tạo Event nhanh và an toàn.
     */
    public static TransferEvent of(
            String transactionId,
            String fromAccountId,
            String fromAccountNumber,
            String toAccountId,
            String toAccountNumber,
            String senderUserId,
            String receiverUserId,
            BigDecimal amount,
            String description
    ) {
        validate(fromAccountId, toAccountId, senderUserId, receiverUserId, amount);

        return TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .transactionId(transactionId)
                .fromAccountId(fromAccountId)
                .fromAccountNumber(fromAccountNumber)
                .senderUserId(senderUserId)
                .toAccountId(toAccountId)
                .toAccountNumber(toAccountNumber)
                .receiverUserId(receiverUserId)
                .amount(amount)
                .currency("VND")
                .description(description)
                .status(TransferStatus.PENDING)
                .timestamp(Instant.now())
                .build();
    }

    private static void validate(
            String fromAccountId,
            String toAccountId,
            String senderUserId,
            String receiverUserId,
            BigDecimal amount
    ) {
        Objects.requireNonNull(fromAccountId, "ID tài khoản nguồn không được rỗng");
        Objects.requireNonNull(toAccountId, "ID tài khoản đích không được rỗng");
        Objects.requireNonNull(senderUserId, "ID người gửi không được rỗng");
        Objects.requireNonNull(receiverUserId, "ID người nhận không được rỗng");
        Objects.requireNonNull(amount, "Số tiền không được rỗng");

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Không thể chuyển tiền đến cùng một tài khoản");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
        }
    }
}