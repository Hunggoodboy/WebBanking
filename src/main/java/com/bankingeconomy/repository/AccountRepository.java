package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Account;
import org.checkerframework.checker.nullness.Opt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByUserId(UUID userId);

    List<Account> findByUserIdAndStatus(UUID userId, Account.AccountStatus status);

    int countByUserId(Long userId);

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);
    @Query(value = "SELECT TOP 2 * FROM accounts ORDER BY NEWID()", nativeQuery = true)
    List<Account> findTwoRandomAccounts();
}
