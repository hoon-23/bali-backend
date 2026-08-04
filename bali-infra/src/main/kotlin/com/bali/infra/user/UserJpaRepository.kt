package com.bali.infra.user

import com.bali.core.user.AuthProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

// Spring Data JPA repository interface for UserJpaEntity.
interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    // Query user by provider and provider-specific ID.
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): UserJpaEntity?
}
