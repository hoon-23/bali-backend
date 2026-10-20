package com.bali.infra.auth

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// Apple refresh token 영속성을 위한 JPA 엔티티 (유저당 1개)
@Entity
@Table(name = "apple_refresh_tokens")
class AppleRefreshTokenJpaEntity(
    @Id
    var userId: UUID = UUID.randomUUID(),

    var refreshToken: String = "",

    var createdAt: Instant = Instant.now(),
)
