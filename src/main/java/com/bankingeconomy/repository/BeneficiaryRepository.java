package com.bankingeconomy.repository;

import com.bankingeconomy.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {
    List<Beneficiary> findByUserId(UUID userId);

    Optional<Beneficiary> findByUserIdAndTargetAccountId(UUID userId, UUID targetAccountId);
}
