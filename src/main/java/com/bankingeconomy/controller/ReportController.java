package com.bankingeconomy.controller;

import com.bankingeconomy.dto.response.ProvinceFlowAmountDTO;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.dto.response.TransactionHistoryItemResponse;
import com.bankingeconomy.dto.response.TransactionStatisticsResponse;
import com.bankingeconomy.entity.User;
import com.bankingeconomy.service.Impl.TransactionProvinceFlowService;
import com.bankingeconomy.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    private TransactionProvinceFlowService flowService;

    @GetMapping("/my-transactions")
    public ResponseData<Map<String, Object>> myTransactions(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String email = currentUser.getEmail();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<TransactionHistoryItemResponse> result =
                reportService.getMyTransactionHistory(email, start, end, pageable);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", result.getContent());
        data.put("page", result.getNumber());
        data.put("size", result.getSize());
        data.put("totalElements", result.getTotalElements());
        data.put("totalPages", result.getTotalPages());

        return new ResponseData<>(200, "Lấy lịch sử giao dịch thành công", data);
    }

    @GetMapping("/my-transactions/statistics")
    public ResponseData<TransactionStatisticsResponse> myTransactionStatistics(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        TransactionStatisticsResponse response = reportService.getMyTransactionStatistics(currentUser.getEmail(), start, end, groupBy);
        return new ResponseData<>(200, "Lấy thống kê giao dịch của người dùng thành công", response);
    }

    @GetMapping("/admin/dashboard-statistics")
    public ResponseData<TransactionStatisticsResponse> adminDashboardStatistics(
            @RequestParam(defaultValue = "day") String groupBy,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        TransactionStatisticsResponse response = reportService.getAdminDashboardStatistics(start, end, groupBy);
        return new ResponseData<>(200, "Lấy thống kê dashboard admin thành công", response);
    }

    @GetMapping("/my-balance")
    public ResponseData<Map<String, Object>> myBalance(@AuthenticationPrincipal User currentUser) {
        return new ResponseData<>(200, "Lấy số dư thành công", Map.of("balance", reportService.getMyBalance(currentUser)));
    }

    @GetMapping("/admin/max-province-flow")
    public ResponseData<List<ProvinceFlowAmountDTO>> maxProvinceFlow(@RequestParam(defaultValue = "5") int amount, @AuthenticationPrincipal User user) throws IOException, InterruptedException, ClassNotFoundException {
        List<ProvinceFlowAmountDTO> data = List.of();
        if (user.getRole().equals(User.Role.ADMIN)) {
            data = flowService.getTopProvinceFlowAmount(amount);
        }
        return new ResponseData<>(200, "Thanh cong", data);
    }
}
