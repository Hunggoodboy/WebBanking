package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatisticsSummaryResponse {
    private long totalTransactions;
    private long successCount;
    private long pendingCount;
    private long failedCount;
    private double totalIn;
    private double totalOut;
    private double totalAmount;
    private double netAmount;
}
