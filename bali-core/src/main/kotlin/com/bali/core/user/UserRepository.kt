package com.bali.core.user

import java.util.UUID

// 사용자 영속성을 위한 포트 인터페이스 - 리포지토리 구현 세부사항을 추상화
interface UserRepository {
    // 고유 식별자로 사용자를 조회, 없으면 null 반환
    fun findById(id: UUID): User?

    // 인증 프로바이더와 프로바이더별 ID로 사용자를 조회, 없으면 null 반환
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?

    // 사용자를 저장하거나 갱신하고, 저장된 사용자를 반환
    fun save(user: User): User
}
