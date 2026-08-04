package com.bali.core.user

import java.util.UUID

// Port interface for user persistence - abstracts repository implementation details
interface UserRepository {
    // Retrieves a user by their unique identifier, returns null if not found
    fun findById(id: UUID): User?

    // Retrieves a user by their auth provider and provider-specific ID, returns null if not found
    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User?

    // Persists or updates a user in the repository, returning the saved user
    fun save(user: User): User
}
