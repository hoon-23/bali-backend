package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import org.springframework.stereotype.Repository
import java.util.UUID

// Spring repository adapter implementing UserRepository port with JPA backend.
@Repository
class UserRepositoryAdapter(
    private val jpaRepository: UserJpaRepository,
) : UserRepository {

    // Retrieve user by unique identifier.
    override fun findById(id: UUID): User? =
        jpaRepository.findById(id).orElse(null)?.toDomain()

    // Query user by OAuth provider and remote ID.
    override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
        jpaRepository.findByProviderAndProviderId(provider, providerId)?.toDomain()

    // Persist user and return domain entity with assigned ID.
    override fun save(user: User): User {
        // Assign new UUID if not present; otherwise use existing.
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

    // Convert JPA entity to domain model.
    private fun UserJpaEntity.toDomain() = User(
        id = id,
        email = email,
        provider = provider,
        providerId = providerId,
        status = status,
        createdAt = createdAt,
    )
}
