package com.bankingeconomy.repository;

import com.bankingeconomy.entity.SavedReceiverAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedReceiverAccountRepository extends JpaRepository<SavedReceiverAccount, UUID> {
    List<SavedReceiverAccount> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<SavedReceiverAccount> findByUserIdAndTargetAccountId(UUID userId, UUID targetAccountId);
}
