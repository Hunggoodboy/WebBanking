package com.bankingeconomy.dto;

import com.bankingeconomy.dto.event.TransferEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HdfsTransactionDTOTest {

    @Test
    void convertFromTransferEventShouldUseEventTimestamp() {
        Instant timestamp = Instant.parse("2026-04-23T10:15:30Z");

        TransferEvent event = TransferEvent.builder()
                .transactionId("11111111-1111-1111-1111-111111111111")
                .fromAccountNumber("123456789012")
                .senderUserId("22222222-2222-2222-2222-222222222222")
                .fromProvince("Ha Noi")
                .fromDistrict("Cau Giay")
                .toAccountNumber("987654321098")
                .receiverUserId("33333333-3333-3333-3333-333333333333")
                .toProvince("Da Nang")
                .toDistrict("Hai Chau")
                .amount(BigDecimal.valueOf(250_000))
                .status(TransferEvent.TransferStatus.COMPLETED)
                .timestamp(timestamp)
                .build();

        HdfsTransactionDTO dto = HdfsTransactionDTO.convertFromTransferEvent(event);

        assertEquals(
                LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()),
                dto.getCreatedAt()
        );
    }
}
