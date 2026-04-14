package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.response.TransactionHistoryItemResponse;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.ReportRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

	private final ReportRepository reportRepository;
	private final UserRepository userRepository;

	@Override
	public Page<TransactionHistoryItemResponse> getMyTransactionHistory(String email, LocalDateTime start, LocalDateTime end, Pageable pageable) {
		User user = userRepository.findByEmail(email)
				.orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

		return reportRepository.findMyTransactionHistory(user.getId(), start, end, pageable)
				.map(transaction -> mapToHistoryItem(transaction, user.getId()));
	}

	private TransactionHistoryItemResponse mapToHistoryItem(Transaction transaction, Long currentUserId) {
		boolean fromMe = transaction.getFromAccount() != null
				&& transaction.getFromAccount().getUser() != null
				&& transaction.getFromAccount().getUser().getId().equals(currentUserId);

		boolean toMe = transaction.getToAccount() != null
				&& transaction.getToAccount().getUser() != null
				&& transaction.getToAccount().getUser().getId().equals(currentUserId);

		String direction = fromMe && toMe ? "SELF" : (fromMe ? "OUT" : (toMe ? "IN" : "UNKNOWN"));
		String counterpartyAccount = fromMe
				? (transaction.getToAccount() != null ? transaction.getToAccount().getAccountNumber() : null)
				: (transaction.getFromAccount() != null ? transaction.getFromAccount().getAccountNumber() : null);

		return TransactionHistoryItemResponse.builder()
				.transactionId(transaction.getId())
				.direction(direction)
				.counterpartyAccount(counterpartyAccount)
				.amount(transaction.getAmount())
				.description(transaction.getDescription())
				.status(transaction.getStatus())
				.createdAt(transaction.getCreatedAt())
				.build();
	}

}
