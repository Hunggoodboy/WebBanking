package com.bankingeconomy.service;

import com.bankingeconomy.dto.response.TransactionHistoryItemResponse;
import com.bankingeconomy.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface ReportService {
	Page<TransactionHistoryItemResponse> getMyTransactionHistory(String email, LocalDateTime start, LocalDateTime end, Pageable pageable);
    double getMyBalance(User user);
}
