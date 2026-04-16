package com.bankingeconomy.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.bankingeconomy.entity.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    // Tìm tất cả audit theo transactionId
    List<AuditLog> findByTransactionId(String transactionId);

    // Tìm audit theo tài khoản (có thể dùng để xem lịch sử của một tài khoản)
    List<AuditLog> findByFromAccountNumberOrToAccountNumberOrderByCreatedAtDesc(
            String fromAccountNumber, String toAccountNumber);

    // Tìm theo trạng thái
    List<AuditLog> findByStatusOrderByCreatedAtDesc(String status);

    List<AuditLog> findAllByOrderByCreatedAtDesc();
}