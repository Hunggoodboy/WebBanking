package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.response.*;
import com.bankingeconomy.dto.response.AdminTopTransferTimeResponse;
import com.bankingeconomy.dto.response.PeakTransferWindowResponse;
import com.bankingeconomy.dto.response.TimeAggregatePointResponse;
import com.bankingeconomy.dto.response.TopTransferUserResponse;
import com.bankingeconomy.entity.Account;
import com.bankingeconomy.entity.Transaction;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.AccountRepository;
import com.bankingeconomy.repository.ReportRepository;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.MapReduceRunnerService;
import com.bankingeconomy.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
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
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportServiceImpl implements ReportService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter HDFS_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy_MM");
    private static final String TOP_TRANSFER_OUTPUT_BASE = "/reports/top_sender_by_time";

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final ObjectProvider<FileSystem> fileSystemProvider;
    private final MapReduceRunnerService mapReduceRunnerService;

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
        String normalizedMonth = normalizeMonth(month);
        YearMonth yearMonth = YearMonth.parse(normalizedMonth);
        String outputDirectory = TOP_TRANSFER_OUTPUT_BASE + "/month=" + normalizedMonth;
        Path reportFile = new Path(outputDirectory + "/part-r-00000");

        FileSystem fileSystem = fileSystemProvider.getIfAvailable();
        if (fileSystem == null) {
            throw new IllegalStateException("HDFS chưa được cấu hình nên không thể đọc kết quả MapReduce.");
        }

        try {
            if (!hasInputFilesForMonth(fileSystem, yearMonth)) {
                return buildEmptyTopTransferResponse(normalizedMonth, outputDirectory, "MAPREDUCE_NO_INPUT", yearMonth);
            }

            boolean reportExists = fileSystem.exists(reportFile);
            if (rerunJob || !reportExists) {
                String result = mapReduceRunnerService.runTopTransferTimeJob(normalizedMonth);
                if (result == null || result.startsWith("ERROR") || "JOB_FAILED".equals(result)) {
                    throw new IllegalStateException("Không chạy được MapReduce cho tháng " + normalizedMonth + ". " + result);
                }
            }

            if (!fileSystem.exists(reportFile)) {
                return buildEmptyTopTransferResponse(normalizedMonth, outputDirectory, "MAPREDUCE_EMPTY", yearMonth);
            }

            return readTopTransferTimeReport(fileSystem, yearMonth, outputDirectory, reportFile);
        } catch (IOException ex) {
            log.error("Lỗi đọc thống kê giờ/ngày cao điểm từ HDFS cho tháng {}", normalizedMonth, ex);
            throw new IllegalStateException("Không đọc được kết quả thống kê từ HDFS.", ex);
        }
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
                .topRecipients(currentUserId != null ? buildTopRecipients(transactions, currentUserId) : List.of())
                .mapReduceTopAccounts(List.of())
                .build();
    }

    private AdminTopTransferTimeResponse readTopTransferTimeReport(FileSystem fileSystem,
                                                                   YearMonth yearMonth,
                                                                   String outputDirectory,
                                                                   Path reportFile) throws IOException {
        Map<Integer, MutableAggregate> hourTotals = new LinkedHashMap<>();
        Map<Integer, MutableAggregate> dayTotals = new LinkedHashMap<>();
        Map<Integer, Map<String, MutableUserAggregate>> hourUsers = new LinkedHashMap<>();
        Map<Integer, Map<String, MutableUserAggregate>> dayUsers = new LinkedHashMap<>();

        try (FSDataInputStream inputStream = fileSystem.open(reportFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                parseTopTransferLine(line, hourTotals, dayTotals, hourUsers, dayUsers);
            }
        }

        Integer peakHourKey = findPeakKey(hourTotals);
        Integer peakDayKey = findPeakKey(dayTotals);

        List<TimeAggregatePointResponse> hourlyChart = buildHourlyChart(hourTotals);
        List<TimeAggregatePointResponse> dailyChart = buildDailyChart(dayTotals, yearMonth);

        List<TopTransferUserResponse> topUsersInPeakHour = buildTopUsers(hourUsers.getOrDefault(peakHourKey, Map.of()));
        List<TopTransferUserResponse> topUsersInPeakDay = buildTopUsers(dayUsers.getOrDefault(peakDayKey, Map.of()));

        return AdminTopTransferTimeResponse.builder()
                .month(yearMonth.toString())
                .source("MAPREDUCE")
                .mapReduceOutputPath(outputDirectory)
                .peakHour(buildPeakHourResponse(peakHourKey, hourTotals.get(peakHourKey)))
                .peakDay(buildPeakDayResponse(peakDayKey, dayTotals.get(peakDayKey)))
                .hourlyChart(hourlyChart)
                .dailyChart(dailyChart)
                .topUsersInPeakHour(topUsersInPeakHour)
                .topUsersInPeakDay(topUsersInPeakDay)
                .build();
    }

    private void parseTopTransferLine(String line,
                                      Map<Integer, MutableAggregate> hourTotals,
                                      Map<Integer, MutableAggregate> dayTotals,
                                      Map<Integer, Map<String, MutableUserAggregate>> hourUsers,
                                      Map<Integer, Map<String, MutableUserAggregate>> dayUsers) {
        String trimmed = String.valueOf(line).trim();
        if (trimmed.isEmpty()) {
            return;
        }

        String[] keyAndValue = trimmed.split("\\t");
        if (keyAndValue.length < 2) {
            return;
        }

        String[] keyParts = keyAndValue[0].split("\\|");
        String[] valueParts = keyAndValue[1].split("\\|");
        if (keyParts.length < 2 || valueParts.length < 2) {
            return;
        }

        long totalTransactions;
        double totalAmount;
        try {
            totalTransactions = Long.parseLong(valueParts[0].trim());
            totalAmount = Double.parseDouble(valueParts[1].trim());
        } catch (NumberFormatException ex) {
            return;
        }

        try {
            switch (keyParts[0]) {
                case "HOUR_TOTAL" -> {
                    int hour = Integer.parseInt(keyParts[1].trim());
                    hourTotals.computeIfAbsent(hour, ignored -> new MutableAggregate()).add(totalTransactions, totalAmount);
                }
                case "DAY_TOTAL" -> {
                    int day = Integer.parseInt(keyParts[1].trim());
                    dayTotals.computeIfAbsent(day, ignored -> new MutableAggregate()).add(totalTransactions, totalAmount);
                }
                case "HOUR_USER" -> {
                    if (keyParts.length < 4) {
                        return;
                    }
                    int hour = Integer.parseInt(keyParts[1].trim());
                    String userId = keyParts[2].trim();
                    String accountNumber = keyParts[3].trim();
                    addUserAggregate(hourUsers, hour, userId, accountNumber, totalTransactions, totalAmount);
                }
                case "DAY_USER" -> {
                    if (keyParts.length < 4) {
                        return;
                    }
                    int day = Integer.parseInt(keyParts[1].trim());
                    String userId = keyParts[2].trim();
                    String accountNumber = keyParts[3].trim();
                    addUserAggregate(dayUsers, day, userId, accountNumber, totalTransactions, totalAmount);
                }
                default -> {
                    // bỏ qua key khác
                }
            }
        } catch (NumberFormatException ignored) {
            // bỏ qua dòng lỗi key
        }
    }

    private void addUserAggregate(Map<Integer, Map<String, MutableUserAggregate>> container,
                                  int bucket,
                                  String userId,
                                  String accountNumber,
                                  long totalTransactions,
                                  double totalAmount) {
        Map<String, MutableUserAggregate> userMap = container.computeIfAbsent(bucket, ignored -> new LinkedHashMap<>());
        String userKey = userId + "|" + accountNumber;
        MutableUserAggregate aggregate = userMap.computeIfAbsent(userKey, ignored -> new MutableUserAggregate(userId, accountNumber));
        aggregate.add(totalTransactions, totalAmount);
    }

    private List<TimeAggregatePointResponse> buildHourlyChart(Map<Integer, MutableAggregate> hourTotals) {
        List<TimeAggregatePointResponse> points = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            MutableAggregate aggregate = hourTotals.get(hour);
            points.add(TimeAggregatePointResponse.builder()
                    .label(formatHourLabel(hour))
                    .totalTransactions(aggregate != null ? aggregate.totalTransactions : 0)
                    .totalAmount(aggregate != null ? aggregate.totalAmount : 0)
                    .build());
        }
        return points;
    }

    private List<TimeAggregatePointResponse> buildDailyChart(Map<Integer, MutableAggregate> dayTotals, YearMonth yearMonth) {
        List<TimeAggregatePointResponse> points = new ArrayList<>();
        for (int day = 1; day <= yearMonth.lengthOfMonth(); day++) {
            MutableAggregate aggregate = dayTotals.get(day);
            points.add(TimeAggregatePointResponse.builder()
                    .label(String.format("Ngày %02d", day))
                    .totalTransactions(aggregate != null ? aggregate.totalTransactions : 0)
                    .totalAmount(aggregate != null ? aggregate.totalAmount : 0)
                    .build());
        }
        return points;
    }

    private PeakTransferWindowResponse buildPeakHourResponse(Integer peakHourKey, MutableAggregate aggregate) {
        if (peakHourKey == null || aggregate == null) {
            return PeakTransferWindowResponse.builder()
                    .label("Không có dữ liệu")
                    .totalTransactions(0)
                    .totalAmount(0)
                    .build();
        }
        return PeakTransferWindowResponse.builder()
                .label(formatHourLabel(peakHourKey))
                .totalTransactions(aggregate.totalTransactions)
                .totalAmount(aggregate.totalAmount)
                .build();
    }

    private PeakTransferWindowResponse buildPeakDayResponse(Integer peakDayKey, MutableAggregate aggregate) {
        if (peakDayKey == null || aggregate == null) {
            return PeakTransferWindowResponse.builder()
                    .label("Không có dữ liệu")
                    .totalTransactions(0)
                    .totalAmount(0)
                    .build();
        }
        return PeakTransferWindowResponse.builder()
                .label(String.format("Ngày %02d", peakDayKey))
                .totalTransactions(aggregate.totalTransactions)
                .totalAmount(aggregate.totalAmount)
                .build();
    }

    private List<TopTransferUserResponse> buildTopUsers(Map<String, MutableUserAggregate> userMap) {
        if (userMap == null || userMap.isEmpty()) {
            return List.of();
        }

        Set<UUID> userIds = userMap.values().stream()
                .map(item -> parseUuid(item.userId))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        return userMap.values().stream()
                .sorted(Comparator
                        .comparingLong((MutableUserAggregate item) -> item.totalTransactions).reversed()
                        .thenComparing((MutableUserAggregate item) -> item.totalAmount, Comparator.reverseOrder())
                        .thenComparing(item -> item.userId))
                .limit(10)
                .map(item -> {
                    UUID userId = parseUuid(item.userId);
                    User user = userId != null ? usersById.get(userId) : null;
                    return TopTransferUserResponse.builder()
                            .userId(item.userId)
                            .fullName(user != null ? user.getFullName() : fallbackUserName(item.userId))
                            .accountNumber(item.accountNumber)
                            .totalTransactions(item.totalTransactions)
                            .totalAmount(item.totalAmount)
                            .build();
                })
                .toList();
    }

    private List<TopRecipientResponse> buildTopRecipients(List<Transaction> transactions, UUID currentUserId) {
        Map<String, MutableTopRecipient> recipientMap = new LinkedHashMap<>();

        for (Transaction transaction : transactions) {
            if (!"SUCCESS".equals(normalizeStatus(transaction.getStatus()))) {
                continue;
            }

            if (!belongsToUser(transaction.getFromAccount(), currentUserId)) {
                continue;
            }

            Account toAccount = transaction.getToAccount();
            if (toAccount == null) {
                continue;
            }

            String accountNumber = toAccount.getAccountNumber() != null ? toAccount.getAccountNumber().trim() : "";
            if (accountNumber.isEmpty()) {
                continue;
            }

            String recipientName = resolveRecipientName(toAccount);
            MutableTopRecipient recipient = recipientMap.computeIfAbsent(accountNumber,
                    key -> new MutableTopRecipient(recipientName, accountNumber));
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

    private String resolveRecipientName(Account account) {
        if (account == null || account.getUser() == null) {
            return "Không rõ người nhận";
        }

        String fullName = Objects.requireNonNullElse(account.getUser().getFullName(), "").trim();
        return fullName.isEmpty() ? "Không rõ người nhận" : fullName;
    }


    private Integer findPeakKey(Map<Integer, MutableAggregate> aggregates) {
        return aggregates.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Integer, MutableAggregate>>comparingLong(entry -> entry.getValue().totalTransactions).reversed()
                        .thenComparing((Map.Entry<Integer, MutableAggregate> entry) -> entry.getValue().totalAmount, Comparator.reverseOrder())
                        .thenComparingInt(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private AdminTopTransferTimeResponse buildEmptyTopTransferResponse(String month,
                                                                       String outputDirectory,
                                                                       String source,
                                                                       YearMonth yearMonth) {
        return AdminTopTransferTimeResponse.builder()
                .month(month)
                .source(source)
                .mapReduceOutputPath(outputDirectory)
                .peakHour(PeakTransferWindowResponse.builder().label("Không có dữ liệu").totalTransactions(0).totalAmount(0).build())
                .peakDay(PeakTransferWindowResponse.builder().label("Không có dữ liệu").totalTransactions(0).totalAmount(0).build())
                .hourlyChart(buildHourlyChart(Map.of()))
                .dailyChart(buildDailyChart(Map.of(), yearMonth))
                .topUsersInPeakHour(List.of())
                .topUsersInPeakDay(List.of())
                .build();
    }

    private boolean hasInputFilesForMonth(FileSystem fileSystem, YearMonth yearMonth) throws IOException {
        int quarter = ((yearMonth.getMonthValue() - 1) / 3) + 1;
        Path inputPattern = new Path(String.format(
                "/data/transactions/province=*/district=*/year=%d/quarter=Q%d/*.csv",
                yearMonth.getYear(),
                quarter
        ));
        FileStatus[] matches = fileSystem.globStatus(inputPattern);
        return matches != null && matches.length > 0;
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

    private String normalizeMonth(String month) {
        try {
            return YearMonth.parse(String.valueOf(month).trim()).toString();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
    }

    private String formatHourLabel(int hour) {
        return String.format("%02d:00 - %02d:59", hour, hour);
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private String fallbackUserName(String userId) {
        if (userId == null || userId.isBlank()) {
            return "Không rõ người dùng";
        }
        return userId.length() > 8 ? "User " + userId.substring(0, 8) : "User " + userId;
    }

    @Override
    public List<TopCustomerResponse> getTop5PercentVipCustomers(int year) {
        LocalDateTime startDate = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        LocalDateTime endDate = LocalDateTime.of(year + 1, 1, 1, 0, 0, 0);

        List<Object[]> rows = reportRepository.findTop5PercentCustomersByYear(startDate, endDate);

        return rows.stream()
                .map(row -> TopCustomerResponse.builder()
                        .userId(row[0] != null ? row[0].toString() : null)
                        .fullName(row[1] != null ? row[1].toString() : null)
                        .email(row[2] != null ? row[2].toString() : null)
                        .phone(row[3] != null ? row[3].toString() : null)
                        .accountNumber(row[4] != null ? row[4].toString() : null)
                        .totalTransferAmount(row[5] != null ? ((Number) row[5]).doubleValue() : 0)
                        .totalTransactions(row[6] != null ? ((Number) row[6]).longValue() : 0)
                        .percentileRank(row[7] != null ? ((Number) row[7]).doubleValue() : 0)
                        .build())
                .toList();
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
    private static class MutableAggregate {
        protected long totalTransactions;
        protected double totalAmount;

        protected void add(long transactions, double amount) {
            this.totalTransactions += transactions;
            this.totalAmount += amount;
        }
    }

    private static class MutableUserAggregate extends MutableAggregate {
        private final String userId;
        private final String accountNumber;

        private MutableUserAggregate(String userId, String accountNumber) {
            this.userId = userId;
            this.accountNumber = accountNumber;
        }
    }
    @Override
public MonthlyReportResponse getMonthlyReport(String email, YearMonth month) {

    // validate user
    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

    // time range của tháng
    LocalDateTime start = month.atDay(1).atStartOfDay();
    LocalDateTime end = month.atEndOfMonth().atTime(23, 59, 59);

    // gọi query DB
    Object[] result = reportRepository.getMonthlySummary(email, start, end);

    double totalIn = 0;
    double totalOut = 0;

    if (result != null) {
        totalIn = result[0] != null ? ((Number) result[0]).doubleValue() : 0;
        totalOut = result[1] != null ? ((Number) result[1]).doubleValue() : 0;
    }

    double profit = totalIn - totalOut;

    return new MonthlyReportResponse(totalIn, totalOut, profit);
}
}
