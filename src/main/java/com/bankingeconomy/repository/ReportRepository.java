package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Transaction, UUID> {

	@Query("""
			SELECT t FROM Transaction t
			WHERE (t.fromAccount.user.id = :userId OR t.toAccount.user.id = :userId)
			  AND (:start IS NULL OR t.createdAt >= :start)
			  AND (:end IS NULL OR t.createdAt <= :end)
			""")
	Page<Transaction> findMyTransactionHistory(UUID userId,
											   LocalDateTime start,
											   LocalDateTime end,
											   Pageable pageable);

}
