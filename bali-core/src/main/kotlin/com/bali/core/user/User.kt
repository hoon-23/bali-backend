package com.bali.core.user

import java.time.Instant
import java.util.UUID

// Domain model representing a user with their authentication and status information
data class User(
    val id: UUID?,
    val email: String,
    val provider: AuthProvider,
    val providerId: String,
    val status: UserStatus,
    val createdAt: Instant,
) {
    // Transitions user status to WITHDRAWN, marking the account as withdrawn
    fun withdraw(): User = copy(status = UserStatus.WITHDRAWN)
}
