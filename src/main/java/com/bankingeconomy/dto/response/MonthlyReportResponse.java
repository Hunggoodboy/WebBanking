package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyReportResponse {
    private double totalIn;
    private double totalOut;
    private double profit;
}
