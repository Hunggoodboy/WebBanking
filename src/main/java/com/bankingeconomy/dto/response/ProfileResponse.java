package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileResponse {
    private String userId;
    private String fullName;
    private String email;
    private String phone;
    private String identityCard;
    private String gender;
    private String role;
    private String createdAt;
    private List<AccountSummaryResponse> accounts;
}
