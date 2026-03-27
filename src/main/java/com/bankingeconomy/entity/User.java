package com.bankingeconomy.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_phone_number", columnList = "phone_number")
        }
)
@Data
public class User {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name="full_name", length = 100)
    private String fullName;

    private String password;

    @Column(length = 100)
    private String email;

    @Column(name = "phone_number", columnDefinition = "VARCHAR(20)")
    private String phoneNumber;

    @Column(name = "created_at")
    private Date createdAt;

    @OneToMany
    private List<Account> accounts;

    @OneToMany
    private List<Notification> notifications;
}
