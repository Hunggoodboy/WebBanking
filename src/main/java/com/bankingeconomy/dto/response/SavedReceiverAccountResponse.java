package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedReceiverAccountResponse {
    private UUID id;
    private UUID targetUserId;
    private UUID targetAccountId;
    private String accountNumber;
    private String accountHolderName;
    private String email;
    private String phone;
    private String status;
    private LocalDateTime updatedAt;
}
