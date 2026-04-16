package com.bankingeconomy.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// TransactionEvent.java — object bắn lên Kafka
@Data
@AllArgsConstructor
public class TransactionEvent {
    private UUID transactionId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
}
