package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Lọc Top 5% khách hàng "đại gia" — chuyển tiền nhiều nhất trong năm.
     * Sử dụng PERCENT_RANK() window function để xếp hạng tại DB level,
     * tất cả JOIN + aggregation + ranking trong 1 query duy nhất → tránh N+1.
     */
    @Query(value = """
            SELECT
                ranked.user_id       AS userId,
                ranked.full_name     AS fullName,
                ranked.email         AS email,
                ranked.phone         AS phone,
                ranked.account_number AS accountNumber,
                ranked.total_amount  AS totalTransferAmount,
                ranked.total_txn     AS totalTransactions,
                ranked.pct_rank      AS percentileRank
            FROM (
                SELECT
                    u.id                AS user_id,
                    u.full_name         AS full_name,
                    u.email             AS email,
                    u.phone             AS phone,
                    a.account_number    AS account_number,
                    SUM(t.amount)       AS total_amount,
                    COUNT(t.id)         AS total_txn,
                    PERCENT_RANK() OVER (ORDER BY SUM(t.amount) ASC) AS pct_rank
                FROM transactions t
                INNER JOIN accounts a ON a.id = t.from_account_id
                INNER JOIN users u    ON u.id = a.user_id
                WHERE t.status = 'SUCCESS'
                  AND t.created_at >= :startDate
                  AND t.created_at < :endDate
                GROUP BY u.id, u.full_name, u.email, u.phone, a.account_number
            ) ranked
            WHERE ranked.pct_rank >= 0.95
            ORDER BY ranked.total_amount DESC
            """, nativeQuery = true)
    List<Object[]> findTop5PercentCustomersByYear(@Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);
    /**
     * Tổng hợp:
     * - totalIn: tiền nhận (toAccount)
     * - totalOut: tiền gửi (fromAccount)
     */
   @Query("""
    SELECT 
        COALESCE(SUM(CASE 
            WHEN ta.user.email = :email THEN t.amount 
            ELSE 0 END), 0),
        COALESCE(SUM(CASE 
            WHEN fa.user.email = :email THEN t.amount 
            ELSE 0 END), 0)
    FROM Transaction t
    LEFT JOIN t.fromAccount fa
    LEFT JOIN fa.user fu
    LEFT JOIN t.toAccount ta
    LEFT JOIN ta.user tu
    WHERE (fu.email = :email OR tu.email = :email)
    AND t.createdAt BETWEEN :start AND :end
""")
Object[] getMonthlySummary(
        @Param("email") String email,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
);
}

