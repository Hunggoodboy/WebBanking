package com.bankingeconomy.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(
        name = "beneficiaries",
        indexes = {
            @Index(name = "idx_user_id", columnList = "user_id")
        }
)
@Data
public class Beneficiary {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(referencedColumnName = "id", name = "user_id")
    private User user;

    @JoinColumn(name = "target_account_id", referencedColumnName = "id")
    private Account targetAccount;
}
