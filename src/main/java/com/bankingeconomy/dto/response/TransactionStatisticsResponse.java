package com.bankingeconomy.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatisticsResponse {
    private String groupBy;
    private String source;
    private TransactionStatisticsSummaryResponse summary;
    private List<StatisticPointResponse> points;
    private List<StatisticBreakdownResponse> statusBreakdown;
    private List<AccountTransferPointResponse> mapReduceTopAccounts;
}
