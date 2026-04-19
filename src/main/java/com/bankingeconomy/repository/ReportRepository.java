package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Transaction, UUID> {

    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN t.fromAccount fa
            LEFT JOIN fa.user fu
            LEFT JOIN t.toAccount ta
            LEFT JOIN ta.user tu
            WHERE (fu.id = :userId OR tu.id = :userId)
              AND (:start IS NULL OR t.createdAt >= :start)
              AND (:end IS NULL OR t.createdAt <= :end)
            """)
    Page<Transaction> findMyTransactionHistory(UUID userId,
                                               LocalDateTime start,
                                               LocalDateTime end,
                                               Pageable pageable);

    @Query("""
            SELECT t FROM Transaction t
            LEFT JOIN t.fromAccount fa
            LEFT JOIN fa.user fu
            LEFT JOIN t.toAccount ta
            LEFT JOIN ta.user tu
            WHERE (fu.id = :userId OR tu.id = :userId)
              AND (:start IS NULL OR t.createdAt >= :start)
              AND (:end IS NULL OR t.createdAt <= :end)
            ORDER BY t.createdAt ASC
            """)
    List<Transaction> findTransactionsForUserStatistics(UUID userId,
                                                        LocalDateTime start,
                                                        LocalDateTime end);

    @Query("""
            SELECT t FROM Transaction t
            WHERE (:start IS NULL OR t.createdAt >= :start)
              AND (:end IS NULL OR t.createdAt <= :end)
            ORDER BY t.createdAt ASC
            """)
    List<Transaction> findTransactionsForAdminStatistics(LocalDateTime start,
                                                         LocalDateTime end);
}
