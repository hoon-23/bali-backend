package com.bali.infra.auth

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// RefreshTokenJpaEntity를 위한 Spring Data JPA 리포지토리 인터페이스.
interface RefreshTokenJpaRepository : JpaRepository<RefreshTokenJpaEntity, UUID> {
    // 토큰 해시로 조회.
    fun findByTokenHash(tokenHash: String): RefreshTokenJpaEntity?
}
