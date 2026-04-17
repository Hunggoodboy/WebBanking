package com.bankingeconomy.repository;

import com.bankingeconomy.dto.HdfsTransactionDTO;
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

    @Query("select new com.bankingeconomy.dto.HdfsTransactionDTO(t.id, t.amount, t.status, t.createdAt, " +
            "fromAcc.accountNumber, fromAcc.user.id, fromAcc.user.province, fromAcc.user.district, " +
            "toAcc.accountNumber, toAcc.user.id, toAcc.user.province, toAcc.user.district) " +
            "from Transaction t " +
            "join t.fromAccount fromAcc " +
            "join t.toAccount toAcc ")
    List<HdfsTransactionDTO> findAllTransactions();
}
