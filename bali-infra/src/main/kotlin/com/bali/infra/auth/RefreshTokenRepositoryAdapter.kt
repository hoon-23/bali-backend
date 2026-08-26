package com.bali.infra.auth

import com.bali.core.auth.RefreshToken
import com.bali.core.auth.RefreshTokenRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// JPA 백엔드로 RefreshTokenRepository 포트를 구현하는 Spring 리포지토리 어댑터.
@Repository
class RefreshTokenRepositoryAdapter(
    private val jpaRepository: RefreshTokenJpaRepository,
) : RefreshTokenRepository {

    // refresh token을 저장하고 ID가 할당된 도메인 엔티티를 반환.
    override fun save(token: RefreshToken): RefreshToken {
        val entity = RefreshTokenJpaEntity(
            id = token.id ?: UUID.randomUUID(),
            userId = token.userId,
            tokenHash = token.tokenHash,
            expiresAt = token.expiresAt,
            revoked = token.revoked,
            createdAt = token.createdAt,
        )
        return jpaRepository.save(entity).toDomain()
    }

    // 토큰 해시로 조회.
    override fun findByTokenHash(tokenHash: String): RefreshToken? =
        jpaRepository.findByTokenHash(tokenHash)?.toDomain()

    // 지정한 id의 토큰이 활성 상태일 때만 원자적으로 폐기 처리 (동시 revokeIfActive 호출 중 하나만 성공).
    @Transactional
    override fun revokeIfActive(id: UUID): Boolean = jpaRepository.revokeIfActive(id) > 0

    // JPA 엔티티를 도메인 모델로 변환.
    private fun RefreshTokenJpaEntity.toDomain() = RefreshToken(
        id = id,
        userId = userId,
        tokenHash = tokenHash,
        expiresAt = expiresAt,
        revoked = revoked,
        createdAt = createdAt,
    )
}
