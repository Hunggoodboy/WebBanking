package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProvinceDensityDTO {
    private String province;
    private int totalTransactions;
    private int rank;
}
