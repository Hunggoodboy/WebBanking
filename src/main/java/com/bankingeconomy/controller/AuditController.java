package com.bankingeconomy.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bankingeconomy.dto.event.TransactionResultEvent;
import com.bankingeconomy.dto.response.ResponseData;
import com.bankingeconomy.entity.AuditLog;
import com.bankingeconomy.service.AuditService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuditController {

    private final AuditService auditService;

    /**
     * API 1: Xem lịch sử audit theo transactionId
     */
    @GetMapping("/transaction/{transactionId}")
    public ResponseData<List<AuditLog>> getAuditByTransactionId(
            @PathVariable String transactionId) {
        
        List<AuditLog> audits = auditService.getAuditByTransactionId(transactionId);
        
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Lấy lịch sử audit theo transaction thành công",
                audits
        );
    }

    /**
     * API 2: Xem lịch sử giao dịch theo số tài khoản
     */
    @GetMapping("/account/{accountNumber}")
    public ResponseData<List<AuditLog>> getAuditByAccountNumber(
            @PathVariable String accountNumber) {
        
        List<AuditLog> audits = auditService.getAuditByAccountNumber(accountNumber);
        
        return new ResponseData<>(
                HttpStatus.OK.value(),
                "Lấy lịch sử giao dịch theo tài khoản thành công",
                audits
        );
    }

    /**
     * API 3: Xem tất cả audit logs (dùng để test)
     */
    @GetMapping("/all")
    public ResponseData<List<AuditLog>> getAllAudits() {
        return new ResponseData<>(200, "Lấy tất cả audit logs thành công", auditService.getAllAudits());
    }
        /**
     * TEST ONLY - Tạo audit log thủ công để kiểm tra Audit + HDFS
     */
    @PostMapping("/test-create")
    public ResponseData<String> testCreateAudit() {
        try {
            // Tạo dữ liệu test
            TransactionResultEvent testEvent = TransactionResultEvent.builder()
                    .transactionId(UUID.randomUUID())
                    .fromAccountId(UUID.randomUUID())
                    .toAccountId(UUID.randomUUID())
                    .fromUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))  // UUID cho user 1
                    .toUserId(UUID.fromString("00000000-0000-0000-0000-000000000002"))    // UUID cho user 2
                    .amount(new BigDecimal("500000"))
                    .description("Test chuyển tiền thành công từ Kiên")
                    .status("COMPLETED")
                    .processedAt(LocalDateTime.now())
                    .build();

            auditService.logAudit(testEvent);

            return new ResponseData<>(
                    HttpStatus.OK.value(),
                    "Test tạo Audit Log + ghi HDFS thành công!",
                    "Kiểm tra database (bảng audit_logs) và HDFS để xác nhận"
            );
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseData<>(
                    HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "Test thất bại: " + e.getMessage(),
                    null
            );
        }
    }
}