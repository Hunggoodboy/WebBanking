package com.bankingeconomy.repository;

import com.bankingeconomy.dto.HdfsTransactionDTO;
import com.bankingeconomy.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    @Query("SELECT t FROM Transaction t WHERE t.fromAccount.id = :accountId OR t.toAccount.id = :accountId")
    Page<Transaction> findAllByAccountId(UUID accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t " +
            "WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId) " +
            "AND t.createdAt >= :start AND t.createdAt < :end")
    Page<Transaction> findAllByAccountIdBetween(
            @Param("accountId") UUID accountId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            Pageable pageable);

    // Cập nhật status giao dịch — dùng trong Kafka consumer sau khi xử lý
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :status WHERE t.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Query("select new com.bankingeconomy.dto.HdfsTransactionDTO(t.id, t.amount, t.status, t.createdAt, " +
            "fromAcc.accountNumber, fromAcc.user.id, fromAcc.user.province, fromAcc.user.district, " +
            "toAcc.accountNumber, toAcc.user.id, toAcc.user.province, toAcc.user.district) " +
            "from Transaction t " +
            "join t.fromAccount fromAcc " +
            "join t.toAccount toAcc ")
    List<HdfsTransactionDTO> findAllTransactions();
}