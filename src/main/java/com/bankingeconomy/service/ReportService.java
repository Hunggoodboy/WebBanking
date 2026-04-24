package com.bankingeconomy.service;

import com.bankingeconomy.dto.response.TopCustomerResponse;
import com.bankingeconomy.dto.response.AdminTopTransferTimeResponse;
import com.bankingeconomy.dto.response.TransactionHistoryItemResponse;
import com.bankingeconomy.dto.response.TransactionStatisticsResponse;
import com.bankingeconomy.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface ReportService {
    Page<TransactionHistoryItemResponse> getMyTransactionHistory(String email, LocalDateTime start, LocalDateTime end, Pageable pageable);
    double getMyBalance(User user);
    TransactionStatisticsResponse getMyTransactionStatistics(String email, LocalDateTime start, LocalDateTime end, String groupBy);
    TransactionStatisticsResponse getAdminDashboardStatistics(LocalDateTime start, LocalDateTime end, String groupBy);
    AdminTopTransferTimeResponse getAdminTopTransferTimeStatistics(String month, boolean rerunJob);
    List<TopCustomerResponse> getTop5PercentVipCustomers(int year);

}
