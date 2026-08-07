package com.bali.infra.user

import com.bali.core.user.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// UserJpaEntity를 위한 Spring Data JPA 리포지토리 인터페이스.
interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    // 프로바이더와 프로바이더별 ID로 사용자를 조회.
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): UserJpaEntity?

    // 특정 상태의 사용자 전체 목록을 조회 (Spring Data가 메서드명으로 쿼리 자동 생성)
    fun findAllByStatus(status: com.bali.core.user.UserStatus): List<UserJpaEntity>
}
