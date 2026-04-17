package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    @Query("SELECT t from Transaction t WHERE t.fromAccount.id = :accountId OR t.toAccount.id = :accountId")
    Page<Transaction> findAllByAccountId(UUID accountId, Pageable pageable);

    @Query("SELECT t from Transaction t " +
            "WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId) " +
            "AND (t.createdAt >= :start OR t.createdAt < :end)")
    Page<Transaction> findAllByAccountIdBetween(UUID accountId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * Lấy tất cả giao dịch kèm eager-load Account → User.
     * Dùng cho bước ETL xuất dữ liệu transaction_fact_geo lên HDFS,
     * cần truy xuất province/district từ User qua Account.
     */
    @Query("SELECT DISTINCT t FROM Transaction t " +
            "JOIN FETCH t.fromAccount fa JOIN FETCH fa.user " +
            "JOIN FETCH t.toAccount ta JOIN FETCH ta.user")
    List<Transaction> findAllWithAccountsAndUsers();
}
