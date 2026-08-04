package com.bali.core.user

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

class UserTest : StringSpec({

    // Creates a new User instance with default test values
    fun newUser() = User(
        id = UUID.randomUUID(),
        email = "test@example.com",
        provider = AuthProvider.GOOGLE,
        providerId = "google-sub-123",
        status = UserStatus.ACTIVE,
        createdAt = Instant.now(),
    )

    "a newly constructed user is ACTIVE by default in tests" {
        newUser().status shouldBe UserStatus.ACTIVE
    }

    "withdraw() transitions status to WITHDRAWN" {
        val withdrawn = newUser().withdraw()
        withdrawn.status shouldBe UserStatus.WITHDRAWN
    }

    "withdraw() is idempotent" {
        val withdrawn = newUser().withdraw().withdraw()
        withdrawn.status shouldBe UserStatus.WITHDRAWN
    }
})
