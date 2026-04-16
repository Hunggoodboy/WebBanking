package com.bankingeconomy.service.Impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.bankingeconomy.dto.event.TransactionResultEvent;
import com.bankingeconomy.entity.AuditLog;
import com.bankingeconomy.repository.AuditLogRepository;
import com.bankingeconomy.service.AuditHdfsService;
import com.bankingeconomy.service.AuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditHdfsService auditHdfsService;

    @Override
    public void logAudit(TransactionResultEvent event) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .transactionId(event.getTransactionId() != null ? event.getTransactionId().toString() : "unknown")
                    .fromAccountNumber(event.getFromAccountId() != null ? event.getFromAccountId().toString() : null)
                    .toAccountNumber(event.getToAccountId() != null ? event.getToAccountId().toString() : null)
                    .amount(event.getAmount() != null ? event.getAmount().doubleValue() : 0.0)
                    .status(event.getStatus())
                    .description(event.getDescription())
                    .failReason(event.getFailReason())
                    .processedAt(event.getProcessedAt() != null ? event.getProcessedAt() : LocalDateTime.now())
                    .build();

            // Lưu vào Database
            auditLogRepository.save(auditLog);
            log.info("✅ AuditLog đã lưu vào DB - Transaction: {}", event.getTransactionId());

            // Ghi lên HDFS
            auditHdfsService.writeAuditToHDFS(auditLog);
        
        } catch (Exception e) {
            log.error("❌ Lỗi ghi Audit cho transaction {}: {}", event.getTransactionId(), e.getMessage(), e);
        }
    }

    @Override
    public List<AuditLog> getAuditByTransactionId(String transactionId) {
        return auditLogRepository.findByTransactionId(transactionId);
    }

    @Override
    public List<AuditLog> getAuditByAccountNumber(String accountNumber) {
        return auditLogRepository.findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
                accountNumber, accountNumber);
    }

    @Override
    public List<AuditLog> getAllAudits() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }
}