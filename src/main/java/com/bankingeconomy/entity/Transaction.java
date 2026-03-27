package com.bankingeconomy.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_user_created_at", columnList = "user_id, created_at")
        }
)
public class Transaction {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "from_account", referencedColumnName = "id")
    private Account fromAccount;

    @ManyToOne
    @JoinColumn(name = "to_account", referencedColumnName = "id")
    private Account toAccount;

    private double amount;

    private String description;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
