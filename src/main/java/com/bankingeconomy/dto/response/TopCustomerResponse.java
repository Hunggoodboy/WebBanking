package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopCustomerResponse {
    private String userId;
    private String fullName;
    private String email;
    private String phone;
    private String accountNumber;
    private double totalTransferAmount;
    private long totalTransactions;
    private double percentileRank;
}
