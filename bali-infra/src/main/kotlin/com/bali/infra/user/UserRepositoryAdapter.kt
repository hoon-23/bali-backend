package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.springframework.stereotype.Repository
import java.util.UUID

// JPA 백엔드로 UserRepository 포트를 구현하는 Spring 리포지토리 어댑터.
@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {

    // 고유 식별자로 사용자를 조회.
    override fun findById(id: UUID): User? =
        jpaRepository.findById(id).orElse(null)?.toDomain()

    // OAuth 프로바이더와 원격 ID로 사용자를 조회.
    override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
        jpaRepository.findByProviderAndProviderId(provider, providerId)?.toDomain()

    // 사용자를 저장하고 ID가 할당된 도메인 엔티티를 반환.
    override fun save(user: User): User {
        // ID가 없으면 새 UUID 할당, 있으면 기존 값 사용.
        val entity = UserJpaEntity(
            id = user.id ?: UUID.randomUUID(),
            email = user.email,
            provider = user.provider,
            providerId = user.providerId,
            status = user.status,
            createdAt = user.createdAt,
        )
        return jpaRepository.save(entity).toDomain()
    }

    // 특정 상태의 사용자 전체 목록을 조회
    override fun findAllByStatus(status: UserStatus): List<User> =
        jpaRepository.findAllByStatus(status).map { it.toDomain() }

    // JPA 엔티티를 도메인 모델로 변환.
    private fun UserJpaEntity.toDomain() = User(
        id = id,
        email = email,
        provider = provider,
        providerId = providerId,
        status = status,
        createdAt = createdAt,
    )
}
