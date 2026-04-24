package com.bankingeconomy.service;

import com.bankingeconomy.dto.response.TopRecipientResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopRecipientStatisticsService {

    private final ReportRepository reportRepository;

    public List<TopRecipientResponse> getTopRecipients(UUID userId, LocalDateTime start, LocalDateTime end) {
        List<Transaction> transactions = reportRepository.findTransactionsForUserStatistics(userId, start, end);
        Map<String, MutableTopRecipient> recipientMap = new LinkedHashMap<>();

        for (Transaction transaction : transactions) {
            if (!"SUCCESS".equals(normalizeStatus(transaction.getStatus()))) {
                continue;
            }

            if (!belongsToUser(transaction.getFromAccount(), userId)) {
                continue;
            }

            Account toAccount = transaction.getToAccount();
            if (toAccount == null) {
                continue;
            }

            String accountNumber = Objects.toString(toAccount.getAccountNumber(), "").trim();
            if (accountNumber.isEmpty()) {
                continue;
            }

            String recipientName = resolveRecipientName(toAccount);
            MutableTopRecipient recipient = recipientMap.computeIfAbsent(
                    accountNumber,
                    key -> new MutableTopRecipient(recipientName, accountNumber)
            );
            recipient.transferCount += 1;
            recipient.totalAmount += transaction.getAmount();
        }

        return recipientMap.values().stream()
                .sorted(Comparator
                        .comparingLong(MutableTopRecipient::getTransferCount).reversed()
                        .thenComparing(MutableTopRecipient::getTotalAmount, Comparator.reverseOrder())
                        .thenComparing(MutableTopRecipient::getFullName, String.CASE_INSENSITIVE_ORDER))
                .limit(5)
                .map(item -> TopRecipientResponse.builder()
                        .fullName(item.fullName)
                        .accountNumber(item.accountNumber)
                        .transferCount(item.transferCount)
                        .totalAmount(item.totalAmount)
                        .build())
                .toList();
    }

    private boolean belongsToUser(Account account, UUID currentUserId) {
        return account != null
                && account.getUser() != null
                && currentUserId.equals(account.getUser().getId());
    }

    private String normalizeStatus(String status) {
        String normalized = Objects.toString(status, "").trim().toUpperCase();
        return normalized.isEmpty() ? "UNKNOWN" : normalized;
    }

    private String resolveRecipientName(Account account) {
        if (account == null || account.getUser() == null) {
            return "Không rõ người nhận";
        }

        String fullName = Objects.toString(account.getUser().getFullName(), "").trim();
        return fullName.isEmpty() ? "Không rõ người nhận" : fullName;
    }

    private static class MutableTopRecipient {
        private final String fullName;
        private final String accountNumber;
        private long transferCount;
        private double totalAmount;

        private MutableTopRecipient(String fullName, String accountNumber) {
            this.fullName = fullName;
            this.accountNumber = accountNumber;
        }

        private long getTransferCount() {
            return transferCount;
        }

        private Double getTotalAmount() {
            return totalAmount;
        }

        private String getFullName() {
            return fullName;
        }
    }
}
