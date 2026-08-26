package com.example.demo.auth.repository;

import com.example.demo.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String token);
    @Modifying
    @Query("""
    update RefreshToken t
    set t.status = com.example.demo.auth.enums.RefreshTokenStatus.ROTATED,
        t.rotatedAt = :now
    where t.tokenHash = :hash
        and t.status = com.example.demo.auth.enums.RefreshTokenStatus.ACTIVE
        and t.expiresAt > :now
        and t.absoluteExpiresAt > :now                
    """)
    int markRotated( @Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Query("""
    update RefreshToken t
    set t.status = com.example.demo.auth.enums.RefreshTokenStatus.REVOKED
    where t.familyId = :familyId    
        and t.status = com.example.demo.auth.enums.RefreshTokenStatus.ACTIVE       

    """)
    int revokeFamily(@Param("familyId") UUID familyId);
}
