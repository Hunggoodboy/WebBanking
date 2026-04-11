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
    Objects.requireNonNull(fromAccountId, "ID tài khoản nguồn không được rỗng");
    Objects.requireNonNull(toAccountId, "ID tài khoản đích không được rỗng");
    Objects.requireNonNull(senderUserId, "ID người dùng thực hiện giao dịch không được rỗng");
    Objects.requireNonNull(receiverUserId, "ID người dùng nhận tiền không được rỗng");
    Objects.requireNonNull(amount, "Số tiền chuyển không được rỗng");

    if (fromAccountId.equals(toAccountId)) {
        throw new IllegalArgumentException("Không thể chuyển tiền đến cùng một tài khoản");
    }

    if (senderUserId.equals(receiverUserId)) {
        throw new IllegalArgumentException("Người gửi và người nhận không được là cùng một người");
    }

    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Số tiền phải lớn hơn 0");
    }
}
}
