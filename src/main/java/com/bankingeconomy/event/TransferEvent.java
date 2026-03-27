package com.bankingeconomy.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferEvent {

    private String eventId;
    private String fromAccountId;
    private String toAccountId;
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

    /** Factory method tiện lợi */
    public static TransferEvent of(String from, String to, BigDecimal amount) {
        return TransferEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .fromAccountId(from)
                .toAccountId(to)
                .amount(amount)
                .currency("VND")
                .timestamp(Instant.now())
                .status(TransferStatus.PENDING)
                .build();
    }
}