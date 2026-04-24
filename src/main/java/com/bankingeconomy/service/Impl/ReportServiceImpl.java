package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.response.AccountTransferPointResponse;
import com.bankingeconomy.dto.response.AdminTopTransferTimeResponse;
import com.bankingeconomy.dto.response.StatisticBreakdownResponse;
import com.bankingeconomy.dto.response.StatisticPointResponse;
import com.bankingeconomy.dto.response.TransactionHistoryItemResponse;
import com.bankingeconomy.dto.response.TransactionStatisticsResponse;
import com.bankingeconomy.dto.response.TransactionStatisticsSummaryResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.ReportRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.AdminTopTransferTimeService;
import com.bankingeconomy.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter HDFS_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ObjectProvider<FileSystem> fileSystemProvider;
    private final AdminTopTransferTimeService adminTopTransferTimeService;

    @Override
    public Page<TransactionHistoryItemResponse> getMyTransactionHistory(String email, LocalDateTime start, LocalDateTime end, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return reportRepository.findMyTransactionHistory(user.getId(), start, end, pageable)
                .map(transaction -> mapToHistoryItem(transaction, user.getId()));
    }

    @Override
    public double getMyBalance(User user) {
        Account account = accountRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return account.getBalance();
    }

    @Override
    public TransactionStatisticsResponse getMyTransactionStatistics(String email, LocalDateTime start, LocalDateTime end, String groupBy) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        List<Transaction> transactions = reportRepository.findTransactionsForUserStatistics(user.getId(), start, end);
        return buildStatisticsResponse(transactions, normalizeGroupBy(groupBy), user.getId(), "DATABASE");
    }

    @Override
    public TransactionStatisticsResponse getAdminDashboardStatistics(LocalDateTime start, LocalDateTime end, String groupBy) {
        List<Transaction> transactions = reportRepository.findTransactionsForAdminStatistics(start, end);
        String normalizedGroupBy = normalizeGroupBy(groupBy);

        TransactionStatisticsResponse response = buildStatisticsResponse(transactions, normalizedGroupBy, null, "DATABASE");
        List<AccountTransferPointResponse> topAccounts = readMapReduceTopAccounts(start, end);
        response.setMapReduceTopAccounts(topAccounts);
        response.setSource(topAccounts.isEmpty() ? "DATABASE_ONLY" : "DATABASE_AND_MAPREDUCE");
        return response;
    }

    @Override
    public AdminTopTransferTimeResponse getAdminTopTransferTimeStatistics(String month, boolean rerunJob) {
        return adminTopTransferTimeService.getTopTransferTimeStatistics(month, rerunJob);
    }

    private TransactionStatisticsResponse buildStatisticsResponse(List<Transaction> transactions,
                                                                 String groupBy,
                                                                 UUID currentUserId,
                                                                 String source) {
        Map<String, MutableStatisticPoint> pointMap = new LinkedHashMap<>();
        Map<String, Long> statusMap = new LinkedHashMap<>();

        long totalTransactions = 0;
        long successCount = 0;
        long pendingCount = 0;
        long failedCount = 0;
        double totalIn = 0;
        double totalOut = 0;
        double totalAmount = 0;

        for (Transaction transaction : transactions) {
            totalTransactions += 1;
            double amount = transaction.getAmount();
            totalAmount += amount;

            String pointLabel = formatPointLabel(transaction.getCreatedAt(), groupBy);
            MutableStatisticPoint point = pointMap.computeIfAbsent(pointLabel, MutableStatisticPoint::new);
            point.totalTransactions += 1;
            point.totalAmount += amount;

            String status = normalizeStatus(transaction.getStatus());
            statusMap.merge(status, 1L, Long::sum);
            if ("SUCCESS".equals(status)) {
                successCount += 1;
            } else if ("PENDING".equals(status)) {
                pendingCount += 1;
            } else if ("FAILED".equals(status)) {
                failedCount += 1;
            }

            if (currentUserId != null) {
                boolean fromMe = belongsToUser(transaction.getFromAccount(), currentUserId);
                boolean toMe = belongsToUser(transaction.getToAccount(), currentUserId);

                if (fromMe && !toMe) {
                    totalOut += amount;
                    point.totalOut += amount;
                } else if (toMe && !fromMe) {
                    totalIn += amount;
                    point.totalIn += amount;
                }
            }
        }

        List<StatisticPointResponse> points = pointMap.values().stream()
                .map(item -> StatisticPointResponse.builder()
                        .label(item.label)
                        .totalTransactions(item.totalTransactions)
                        .totalIn(item.totalIn)
                        .totalOut(item.totalOut)
                        .totalAmount(item.totalAmount)
                        .build())
                .toList();

        List<StatisticBreakdownResponse> statusBreakdown = statusMap.entrySet().stream()
                .map(entry -> StatisticBreakdownResponse.builder()
                        .label(entry.getKey())
                        .value(entry.getValue())
                        .build())
                .toList();

        TransactionStatisticsSummaryResponse summary = TransactionStatisticsSummaryResponse.builder()
                .totalTransactions(totalTransactions)
                .successCount(successCount)
                .pendingCount(pendingCount)
                .failedCount(failedCount)
                .totalIn(totalIn)
                .totalOut(totalOut)
                .totalAmount(totalAmount)
                .netAmount(totalIn - totalOut)
                .build();

        return TransactionStatisticsResponse.builder()
                .groupBy(groupBy)
                .source(source)
                .summary(summary)
                .points(points)
                .statusBreakdown(statusBreakdown)
                .mapReduceTopAccounts(List.of())
                .build();
    }

    private List<AccountTransferPointResponse> readMapReduceTopAccounts(LocalDateTime start, LocalDateTime end) {
        FileSystem fileSystem = fileSystemProvider.getIfAvailable();
        if (fileSystem == null) {
            return List.of();
        }

        Map<String, Double> totals = new LinkedHashMap<>();
        for (YearMonth yearMonth : resolveYearMonths(start, end)) {
            String monthFolder = yearMonth.format(HDFS_MONTH_FORMAT);
            Path reportPath = new Path("/banking/reports/total_by_account_" + monthFolder + "/part-r-00000");

            try {
                if (!fileSystem.exists(reportPath)) {
                    continue;
                }

                try (FSDataInputStream inputStream = fileSystem.open(reportPath);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.isEmpty()) {
                            continue;
                        }

                        String[] parts = trimmed.split("\\s+");
                        if (parts.length < 2) {
                            continue;
                        }

                        String accountNumber = parts[0].trim();
                        double amount;
                        try {
                            amount = Double.parseDouble(parts[parts.length - 1].trim());
                        } catch (NumberFormatException ex) {
                            continue;
                        }

                        totals.merge(accountNumber, amount, Double::sum);
                    }
                }
            } catch (IOException ex) {
                log.warn("Không đọc được kết quả MapReduce tại {}", reportPath, ex);
            }
        }

        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(entry -> AccountTransferPointResponse.builder()
                        .accountNumber(entry.getKey())
                        .totalAmount(entry.getValue())
                        .build())
                .toList();
    }

    private List<YearMonth> resolveYearMonths(LocalDateTime start, LocalDateTime end) {
        LocalDateTime effectiveEnd = end != null ? end : LocalDateTime.now();
        LocalDateTime effectiveStart = start != null ? start : effectiveEnd.minusMonths(2).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        YearMonth startMonth = YearMonth.from(effectiveStart);
        YearMonth endMonth = YearMonth.from(effectiveEnd);
        List<YearMonth> months = new ArrayList<>();

        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }

        return months;
    }

    private boolean belongsToUser(Account account, UUID currentUserId) {
        return account != null
                && account.getUser() != null
                && currentUserId.equals(account.getUser().getId());
    }

    private String normalizeGroupBy(String groupBy) {
        String normalized = String.valueOf(groupBy).trim().toLowerCase(Locale.ROOT);
        if ("month".equals(normalized) || "year".equals(normalized)) {
            return normalized;
        }
        return "day";
    }

    private String normalizeStatus(String status) {
        String normalized = String.valueOf(status).trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? "UNKNOWN" : normalized;
    }

    private String formatPointLabel(LocalDateTime createdAt, String groupBy) {
        if (createdAt == null) {
            return "Không rõ";
        }
        if ("month".equals(groupBy)) {
            return createdAt.format(MONTH_FORMAT);
        }
        if ("year".equals(groupBy)) {
            return createdAt.format(YEAR_FORMAT);
        }
        return createdAt.format(DAY_FORMAT);
    }

    private TransactionHistoryItemResponse mapToHistoryItem(Transaction transaction, UUID currentUserId) {
        boolean fromMe = belongsToUser(transaction.getFromAccount(), currentUserId);
        boolean toMe = belongsToUser(transaction.getToAccount(), currentUserId);

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

    private static class MutableStatisticPoint {
        private final String label;
        private long totalTransactions;
        private double totalIn;
        private double totalOut;
        private double totalAmount;

        private MutableStatisticPoint(String label) {
            this.label = label;
        }
    }

}
