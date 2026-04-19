package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatisticPointResponse {
    private String label;
    private long totalTransactions;
    private double totalIn;
    private double totalOut;
    private double totalAmount;
}
