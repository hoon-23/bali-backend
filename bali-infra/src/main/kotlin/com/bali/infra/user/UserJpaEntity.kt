package com.bali.infra.user

import com.bali.core.user.AuthProvider
import com.bali.core.user.UserStatus
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

// JPA entity for User domain model persistence.
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(columnNames = ["provider", "provider_id"])],
)
class UserJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),

    var email: String = "",

    @Enumerated(EnumType.STRING)
    var provider: AuthProvider = AuthProvider.GOOGLE,

    var providerId: String = "",

    @Enumerated(EnumType.STRING)
    var status: UserStatus = UserStatus.ACTIVE,

    var createdAt: Instant = Instant.now(),
)
