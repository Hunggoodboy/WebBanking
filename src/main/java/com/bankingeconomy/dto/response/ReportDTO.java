package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReportDTO {
    private String userAndPeriod;
    private String totalAmount; // Để String để tránh bị format lại thành E7
}
