package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BeneficiaryLookupResponse {
    private UUID userId;
    private UUID accountId;
    private String fullName;
    private String email;
    private String phone;
    private String accountNumber;
    private boolean alreadySaved;
}
