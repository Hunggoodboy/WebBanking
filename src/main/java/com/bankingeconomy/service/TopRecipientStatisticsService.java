package com.bankingeconomy.service;

import com.bankingeconomy.dto.response.TopRecipientResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TopRecipientStatisticsService {

    private static final Path HDFS_TRANSACTION_PATH = new Path("/data/transactions");

    private final ReportRepository reportRepository;
    private final AccountRepository accountRepository;
    private final ObjectProvider<FileSystem> fileSystemProvider;

    public List<TopRecipientResponse> getTopRecipients(UUID userId, LocalDateTime start, LocalDateTime end) {
        List<TopRecipientResponse> hdfsTopRecipients = getTopRecipientsFromHdfs(userId, start, end);
        if (!hdfsTopRecipients.isEmpty()) {
            return hdfsTopRecipients;
        }

        return getTopRecipientsFromDatabase(userId, start, end);
    }

    private List<TopRecipientResponse> getTopRecipientsFromDatabase(UUID userId, LocalDateTime start, LocalDateTime end) {
        List<Transaction> transactions = reportRepository.findTransactionsForUserStatistics(userId, start, end);
        Map<String, MutableTopRecipient> recipientMap = new LinkedHashMap<>();

        for (Transaction transaction : transactions) {
            if (!isSuccessfulStatus(transaction.getStatus())) {
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

        return toResponse(recipientMap);
    }

    private List<TopRecipientResponse> getTopRecipientsFromHdfs(UUID userId, LocalDateTime start, LocalDateTime end) {
        FileSystem fileSystem = fileSystemProvider.getIfAvailable();
        if (fileSystem == null) {
            return List.of();
        }

        Map<String, MutableTopRecipient> recipientMap = new LinkedHashMap<>();
        Map<String, String> recipientNameCache = new HashMap<>();

        try {
            if (!fileSystem.exists(HDFS_TRANSACTION_PATH)) {
                return List.of();
            }

            RemoteIterator<LocatedFileStatus> files = fileSystem.listFiles(HDFS_TRANSACTION_PATH, true);
            while (files.hasNext()) {
                LocatedFileStatus file = files.next();
                if (!file.isFile() || !file.getPath().getName().endsWith(".csv")) {
                    continue;
                }

                readTopRecipientsFromHdfsFile(fileSystem, file.getPath(), userId, start, end, recipientMap, recipientNameCache);
            }
        } catch (IOException ex) {
            log.warn("Không đọc được dữ liệu top người nhận từ HDFS", ex);
            return List.of();
        }

        return toResponse(recipientMap);
    }

    private void readTopRecipientsFromHdfsFile(FileSystem fileSystem,
                                               Path filePath,
                                               UUID userId,
                                               LocalDateTime start,
                                               LocalDateTime end,
                                               Map<String, MutableTopRecipient> recipientMap,
                                               Map<String, String> recipientNameCache) throws IOException {
        try (FSDataInputStream inputStream = fileSystem.open(filePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                HdfsTransactionFact fact = parseHdfsTransactionFact(line);
                if (fact == null
                        || !"OUT".equalsIgnoreCase(fact.direction)
                        || !userId.equals(fact.ownerUserId)
                        || !isSuccessfulStatus(fact.status)
                        || !isInRange(fact.createdAt, start, end)
                        || fact.counterpartyAccountNumber.isBlank()) {
                    continue;
                }

                String recipientName = recipientNameCache.computeIfAbsent(
                        fact.counterpartyAccountNumber,
                        this::resolveRecipientNameByAccountNumber
                );
                MutableTopRecipient recipient = recipientMap.computeIfAbsent(
                        fact.counterpartyAccountNumber,
                        key -> new MutableTopRecipient(recipientName, fact.counterpartyAccountNumber)
                );
                recipient.transferCount += 1;
                recipient.totalAmount += fact.amount;
            }
        }
    }

    private HdfsTransactionFact parseHdfsTransactionFact(String line) {
        String trimmed = Objects.toString(line, "").trim();
        if (trimmed.isEmpty() || trimmed.startsWith("transaction_id,")) {
            return null;
        }

        String[] parts = trimmed.split(",", -1);
        if (parts.length < 8) {
            return null;
        }

        try {
            return new HdfsTransactionFact(
                    UUID.fromString(parts[1].trim()),
                    parts[3].trim(),
                    parts[4].trim(),
                    Double.parseDouble(parts[5].trim()),
                    parts[6].trim(),
                    LocalDateTime.parse(parts[7].trim())
            );
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return null;
        }
    }

    private boolean isInRange(LocalDateTime createdAt, LocalDateTime start, LocalDateTime end) {
        return createdAt != null
                && (start == null || !createdAt.isBefore(start))
                && (end == null || !createdAt.isAfter(end));
    }

    private List<TopRecipientResponse> toResponse(Map<String, MutableTopRecipient> recipientMap) {
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

    private boolean isSuccessfulStatus(String status) {
        String normalizedStatus = normalizeStatus(status);
        return "SUCCESS".equals(normalizedStatus) || "COMPLETED".equals(normalizedStatus);
    }

    private String resolveRecipientName(Account account) {
        if (account == null || account.getUser() == null) {
            return "Không rõ người nhận";
        }

        String fullName = Objects.toString(account.getUser().getFullName(), "").trim();
        return fullName.isEmpty() ? "Không rõ người nhận" : fullName;
    }

    private String resolveRecipientNameByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(this::resolveRecipientName)
                .orElse("Không rõ người nhận");
    }

    private record HdfsTransactionFact(UUID ownerUserId,
                                       String direction,
                                       String counterpartyAccountNumber,
                                       double amount,
                                       String status,
                                       LocalDateTime createdAt) {
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
