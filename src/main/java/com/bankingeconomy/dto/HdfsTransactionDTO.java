package com.bankingeconomy.dto;

import com.bankingeconomy.dto.event.TransferEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
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

    public static HdfsTransactionDTO convertFromTransferEvent(TransferEvent transferEvent) {
        HdfsTransactionDTO hdfsTransactionDTO = HdfsTransactionDTO.builder()
                .transactionId(UUID.fromString(transferEvent.getTransactionId()))
                .amount(Double.parseDouble(transferEvent.getAmount().toString()))
                .status(transferEvent.getStatus().name())
                .createdAt(LocalDateTime.now()) // Hoặc lấy từ event nếu có
                .fromAccountNumber(transferEvent.getFromAccountNumber())
                .fromUserId(UUID.fromString(transferEvent.getSenderUserId()))
                .fromProvince(transferEvent.getFromProvince())
                .fromDistrict(transferEvent.getFromDistrict())
                .toAccountNumber(transferEvent.getToAccountNumber())
                .toUserId(UUID.fromString(transferEvent.getReceiverUserId()))
                .ToProvince(transferEvent.getToProvince())
                .ToDistrict(transferEvent.getToDistrict())
                .build();
        return hdfsTransactionDTO;
    }
}
