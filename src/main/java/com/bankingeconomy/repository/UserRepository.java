package com.bankingeconomy.repository;

import com.bankingeconomy.entity.User;
import org.apache.hadoop.thirdparty.org.checkerframework.checker.nullness.Opt;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    int countByEmail(String email);
}
