package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryItemResponse {
    private UUID transactionId;
    private String direction;
    private String counterpartyAccount;
    private double amount;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
