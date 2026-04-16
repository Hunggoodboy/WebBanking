package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountSummaryResponse {
    private String id;
    private String accountNumber;
    private double balance;
    private String status;
    private String createdAt;
}
