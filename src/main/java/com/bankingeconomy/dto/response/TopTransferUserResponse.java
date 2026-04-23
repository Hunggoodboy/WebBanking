package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopTransferUserResponse {
    private String userId;
    private String fullName;
    private String accountNumber;
    private long totalTransactions;
    private double totalAmount;
}
