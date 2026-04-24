package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopRecipientResponse {
    private String fullName;
    private String accountNumber;
    private long transferCount;
    private double totalAmount;
}
