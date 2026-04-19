package com.bankingeconomy.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_to_account_id", columnList = "to_account_id"),
                @Index(name = "idx_from_and_to_account_id", columnList = "from_account_id, to_account_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "from_account_id", referencedColumnName = "id")
    private Account fromAccount;

    @ManyToOne
    @JoinColumn(name = "to_account_id", referencedColumnName = "id")
    private Account toAccount;

    private double amount;

    private String description;

    @Column(length = 20)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
