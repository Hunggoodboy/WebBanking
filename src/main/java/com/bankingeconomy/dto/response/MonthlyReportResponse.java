package com.bankingeconomy.dto.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyReportResponse {
    private double totalIn;
    private double totalOut;
    private double profit;
}
