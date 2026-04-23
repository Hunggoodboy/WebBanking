package com.bankingeconomy.dto;

import com.bankingeconomy.dto.event.TransferEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    private String fromAccountNumber;
    private UUID fromUserId;
    private String fromProvince;
    private String fromDistrict;

    private String toAccountNumber;
    private UUID toUserId;
    private String toProvince;
    private String toDistrict;

    public static HdfsTransactionDTO convertFromTransferEvent(TransferEvent transferEvent) {
        return HdfsTransactionDTO.builder()
                .transactionId(UUID.fromString(transferEvent.getTransactionId()))
                .amount(transferEvent.getAmount().doubleValue())
                .status(transferEvent.getStatus().name())
                .createdAt(resolveCreatedAt(transferEvent.getTimestamp()))
                .fromAccountNumber(transferEvent.getFromAccountNumber())
                .fromUserId(UUID.fromString(transferEvent.getSenderUserId()))
                .fromProvince(transferEvent.getFromProvince())
                .fromDistrict(transferEvent.getFromDistrict())
                .toAccountNumber(transferEvent.getToAccountNumber())
                .toUserId(UUID.fromString(transferEvent.getReceiverUserId()))
                .toProvince(transferEvent.getToProvince())
                .toDistrict(transferEvent.getToDistrict())
                .build();
    }

    private static LocalDateTime resolveCreatedAt(Instant eventTimestamp) {
        Instant timestamp = eventTimestamp != null ? eventTimestamp : Instant.now();
        return LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault());
    }
}
