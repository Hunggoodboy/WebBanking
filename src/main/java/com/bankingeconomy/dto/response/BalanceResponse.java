package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BalanceResponse {

    private String accountNumber;
    private double balance;
    private String source; // "CACHE" hoặc "DATABASE"
}
