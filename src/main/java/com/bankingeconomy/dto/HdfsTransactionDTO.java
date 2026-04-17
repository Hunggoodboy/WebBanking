package com.bankingeconomy.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HdfsTransactionDTO {
    private UUID transactionId;
    private double amount;
    private String status;
    private LocalDateTime createdAt;

    // Phía người gửi
    private String fromAccountNumber;
    private UUID fromUserId;
    private String fromProvince;
    private String fromDistrict;

    // Phía người nhận
    private String toAccountNumber;
    private UUID toUserId;
    private String ToProvince;
    private String ToDistrict;
}
