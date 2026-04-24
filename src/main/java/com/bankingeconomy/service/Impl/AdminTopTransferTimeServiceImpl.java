package com.bankingeconomy.service.Impl;

import com.bankingeconomy.dto.response.AdminTopTransferTimeResponse;
import com.bankingeconomy.dto.response.PeakTransferWindowResponse;
import com.bankingeconomy.dto.response.TimeAggregatePointResponse;
import com.bankingeconomy.dto.response.TopTransferUserResponse;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.exception.AppException;
import com.bankingeconomy.exception.ErrorCode;
import com.bankingeconomy.repository.UserRepository;
import com.bankingeconomy.service.AdminTopTransferTimeService;
import com.bankingeconomy.service.MapReduceRunnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminTopTransferTimeServiceImpl implements AdminTopTransferTimeService {

    private static final String TOP_TRANSFER_OUTPUT_BASE = "/reports/top_sender_by_time";

    private final ObjectProvider<FileSystem> fileSystemProvider;
    private final MapReduceRunnerService mapReduceRunnerService;
    private final UserRepository userRepository;

    @Override
    public AdminTopTransferTimeResponse getTopTransferTimeStatistics(String month, boolean rerunJob) {
        String normalizedMonth = normalizeMonth(month);
        YearMonth yearMonth = YearMonth.parse(normalizedMonth);
        String outputDirectory = TOP_TRANSFER_OUTPUT_BASE + "/month=" + normalizedMonth;
        Path reportFile = new Path(outputDirectory + "/part-r-00000");

        FileSystem fileSystem = fileSystemProvider.getIfAvailable();
        if (fileSystem == null) {
            throw new IllegalStateException("HDFS chÆ°a Ä‘Æ°á»£c cáº¥u hÃ¬nh nÃªn khÃ´ng thá»ƒ Ä‘á»c káº¿t quáº£ MapReduce.");
        }

        try {
            if (!hasInputFilesForMonth(fileSystem, yearMonth)) {
                return buildEmptyTopTransferResponse(normalizedMonth, outputDirectory, "MAPREDUCE_NO_INPUT", yearMonth);
            }

            boolean reportExists = fileSystem.exists(reportFile);
            if (rerunJob || !reportExists) {
                String result = mapReduceRunnerService.runTopTransferTimeJob(normalizedMonth);
                if (result == null || result.startsWith("ERROR") || "JOB_FAILED".equals(result)) {
                    throw new IllegalStateException("KhÃ´ng cháº¡y Ä‘Æ°á»£c MapReduce cho thÃ¡ng " + normalizedMonth + ". " + result);
                }
            }

            if (!fileSystem.exists(reportFile)) {
                return buildEmptyTopTransferResponse(normalizedMonth, outputDirectory, "MAPREDUCE_EMPTY", yearMonth);
            }

            return readTopTransferTimeReport(fileSystem, yearMonth, outputDirectory, reportFile);
        } catch (IOException ex) {
            log.error("Lá»—i Ä‘á»c thá»‘ng kÃª giá»/ngÃ y cao Ä‘iá»ƒm tá»« HDFS cho thÃ¡ng {}", normalizedMonth, ex);
            throw new IllegalStateException("KhÃ´ng Ä‘á»c Ä‘Æ°á»£c káº¿t quáº£ thá»‘ng kÃª tá»« HDFS.", ex);
        }
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
                    // bá» qua key khÃ¡c
                }
            }
        } catch (NumberFormatException ignored) {
            // bá» qua dÃ²ng lá»—i key
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
                    .label(String.format("NgÃ y %02d", day))
                    .totalTransactions(aggregate != null ? aggregate.totalTransactions : 0)
                    .totalAmount(aggregate != null ? aggregate.totalAmount : 0)
                    .build());
        }
        return points;
    }

    private PeakTransferWindowResponse buildPeakHourResponse(Integer peakHourKey, MutableAggregate aggregate) {
        if (peakHourKey == null || aggregate == null) {
            return PeakTransferWindowResponse.builder()
                    .label("KhÃ´ng cÃ³ dá»¯ liá»‡u")
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
                    .label("KhÃ´ng cÃ³ dá»¯ liá»‡u")
                    .totalTransactions(0)
                    .totalAmount(0)
                    .build();
        }
        return PeakTransferWindowResponse.builder()
                .label(String.format("NgÃ y %02d", peakDayKey))
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
                .filter(Objects::nonNull)
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
                .peakHour(PeakTransferWindowResponse.builder().label("KhÃ´ng cÃ³ dá»¯ liá»‡u").totalTransactions(0).totalAmount(0).build())
                .peakDay(PeakTransferWindowResponse.builder().label("KhÃ´ng cÃ³ dá»¯ liá»‡u").totalTransactions(0).totalAmount(0).build())
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
            return "KhÃ´ng rÃµ ngÆ°á»i dÃ¹ng";
        }
        return userId.length() > 8 ? "User " + userId.substring(0, 8) : "User " + userId;
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
}
