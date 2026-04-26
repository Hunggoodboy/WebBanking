package com.bankingeconomy.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO dùng cho thống kê nhiều tháng
 * Ví dụ:
 * 2026-01 → tổng thu, tổng chi, lãi/lỗ
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MonthlyReportItemResponse {

    /**
     * Format: yyyy-MM (ví dụ: 2026-01)
     */
    private String month;

    /**
     * Tổng tiền nhận trong tháng
     */
    private double totalIn;

    /**
     * Tổng tiền gửi trong tháng
     */
    private double totalOut;

    /**
     * Lãi / lỗ = totalIn - totalOut
     */
    private double profit;
}
