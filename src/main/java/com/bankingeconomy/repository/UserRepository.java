package com.bankingeconomy.repository;

import com.bankingeconomy.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phoneNumber);

    boolean existsByPhone(String phoneNumber);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    int countByEmail(String email);
}
