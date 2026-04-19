package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSummaryResponse {
    private UUID id;
    private String accountNumber;
    private double balance;
    private String status;
    private String name;
    private boolean primary;
}
