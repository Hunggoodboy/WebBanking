package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder; // Thêm dòng này
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder // Thêm dòng này để dùng được hàm .builder()
@AllArgsConstructor
@NoArgsConstructor
public class TransferResponse {
    private String message;
    private String transactionId;
}