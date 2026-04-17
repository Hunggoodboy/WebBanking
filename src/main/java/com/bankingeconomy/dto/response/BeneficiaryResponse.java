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
public class BeneficiaryResponse {
    private UUID id;
    private UUID targetUserId;
    private UUID targetAccountId;
    private String fullName;
    private String email;
    private String phone;
    private String accountNumber;
}
