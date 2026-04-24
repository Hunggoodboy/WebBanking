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
public class AdminTopTransferTimeResponse {
    private String month;
    private String source;
    private String mapReduceOutputPath;
    private PeakTransferWindowResponse peakHour;
    private PeakTransferWindowResponse peakDay;
    private List<TimeAggregatePointResponse> hourlyChart;
    private List<TimeAggregatePointResponse> dailyChart;
    private List<TopTransferUserResponse> topUsersInPeakHour;
    private List<TopTransferUserResponse> topUsersInPeakDay;
}
