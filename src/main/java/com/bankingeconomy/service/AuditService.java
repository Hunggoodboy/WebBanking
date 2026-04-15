package com.bankingeconomy.service;

import java.util.List;

import com.bankingeconomy.dto.event.TransactionResultEvent;
import com.bankingeconomy.entity.AuditLog;

public interface AuditService {

    /**
     * Ghi audit log từ kết quả giao dịch (consume từ Kafka)
     */
    void logAudit(TransactionResultEvent event);

    /**
     * Lấy lịch sử audit theo transactionId
     */
    List<AuditLog> getAuditByTransactionId(String transactionId);

    /**
     * Lấy lịch sử audit theo số tài khoản (from hoặc to)
     */
    List<AuditLog> getAuditByAccountNumber(String accountNumber);
}