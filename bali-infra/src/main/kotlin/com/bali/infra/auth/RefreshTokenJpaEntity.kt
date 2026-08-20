package com.bali.infra.auth

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// RefreshToken 도메인 모델 영속성을 위한 JPA 엔티티.
@Entity
@Table(name = "refresh_tokens")
class RefreshTokenJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    var userId: UUID = UUID.randomUUID(),

    var tokenHash: String = "",

    var expiresAt: Instant = Instant.now(),

    var revoked: Boolean = false,

    var createdAt: Instant = Instant.now(),
)
