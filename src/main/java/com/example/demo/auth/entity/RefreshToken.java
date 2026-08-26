package com.example.demo.auth.entity;

import com.example.demo.auth.enums.RefreshTokenStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.UuidGenerator;


import java.time.Instant;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name = "refresh_tokens")
@ToString
@DynamicInsert
@Builder
public class RefreshToken {
    @Id
    @Column(name = "id")
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "token_hash")
    @ToString.Exclude
    private String tokenHash;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private RefreshTokenStatus status;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "absolute_expires_at")
    private Instant absoluteExpiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "family_id")
    private UUID familyId;

    @Transient
    @ToString.Exclude
    private String tokenString;
}
