package com.bankingeconomy.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProvinceFlowAmountDTO {
    private String from;
    private String to;
    private Double total;
}
