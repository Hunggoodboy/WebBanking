package com.bankingeconomy.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bankingeconomy.service.QuarterlyGrowthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/admin/quarterly-growth")
@RequiredArgsConstructor
public class QuarterlyGrowthController {

    private final QuarterlyGrowthService quarterlyGrowthService;

    /**
     * Chạy MapReduce Job để tính tăng trưởng theo quý
     * POST /api/admin/quarterly-growth/run?year=2024
     */
    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> runJob(@RequestParam(defaultValue = "2024") String year) {
        log.info("Admin yêu cầu chạy QuarterlyGrowth job cho năm: {}", year);
        String message = quarterlyGrowthService.runQuarterlyGrowthJob(year);
        return ResponseEntity.ok(message);
    }

    /**
     * Lấy kết quả so sánh Q1 vs Q2
     * GET /api/admin/quarterly-growth/result?year=2024
     */
    @GetMapping("/result")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getResult(
            @RequestParam(defaultValue = "2024") String year) {
        log.info("Lấy kết quả QuarterlyGrowth cho năm: {}", year);
        Map<String, Object> result = quarterlyGrowthService.getQuarterlyGrowthResult(year);
        return ResponseEntity.ok(result);
    }
}