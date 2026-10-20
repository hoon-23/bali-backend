package com.bali.infra.auth

import com.bali.core.auth.AppleRefreshTokenRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// JPA 백엔드로 AppleRefreshTokenRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class AppleRefreshTokenRepositoryAdapter(
    private val jpaRepository: AppleRefreshTokenJpaRepository,
) : AppleRefreshTokenRepository {

    // 유저당 1개만 유지하므로 PK(userId) 기준으로 덮어쓴다
    @Transactional
    override fun save(userId: UUID, refreshToken: String) {
        jpaRepository.save(AppleRefreshTokenJpaEntity(userId = userId, refreshToken = refreshToken, createdAt = Instant.now()))
    }

    override fun findByUserId(userId: UUID): String? =
        jpaRepository.findById(userId).map { it.refreshToken }.orElse(null)

    @Transactional
    override fun deleteByUserId(userId: UUID) {
        if (jpaRepository.existsById(userId)) jpaRepository.deleteById(userId)
    }
}
