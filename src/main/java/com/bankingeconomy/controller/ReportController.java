package com.bankingeconomy.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account/report")
public class ReportController {

    @GetMapping("/summary")
    public ResponseEntity<ReportDTO> getMonthlySummary(
            @RequestParam UUID accountId,
            @RequestParam @DateTimeFormat(iso = DATE) LocalDate month) {
        return ResponseEntity.ok(reportService.getMonthlySummary(accountId, month));
    }
    // Theo ngày
    // theo năm
}
