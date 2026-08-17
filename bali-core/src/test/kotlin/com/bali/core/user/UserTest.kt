package com.bali.core.user

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow
import java.time.Instant
import java.util.UUID

class UserTest : StringSpec({

    // 테스트용 기본값으로 새 User 인스턴스를 생성
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

    "updateProfile은 전달된 필드만 바꾸고 나머지는 유지한다" {
        val user = newUser().copy(nickname = "원래닉네임", weeklyGoalSessions = 3)

        val updated = user.updateProfile(nickname = "새닉네임", weeklyGoalSessions = null)

        updated.nickname shouldBe "새닉네임"
        updated.weeklyGoalSessions shouldBe 3
    }

    "updateProfile에 둘 다 null이면 아무것도 안 바뀐다" {
        val user = newUser().copy(nickname = "그대로", weeklyGoalSessions = 5)

        val updated = user.updateProfile(nickname = null, weeklyGoalSessions = null)

        updated.nickname shouldBe "그대로"
        updated.weeklyGoalSessions shouldBe 5
    }

    "updateProfile은 nickname 앞뒤 공백을 trim한다" {
        val user = newUser()

        val updated = user.updateProfile(nickname = "  공백닉네임  ", weeklyGoalSessions = null)

        updated.nickname shouldBe "공백닉네임"
    }

    "updateProfile에 공백만 있는 nickname을 주면 예외를 던진다" {
        val user = newUser()

        shouldThrow<IllegalArgumentException> {
            user.updateProfile(nickname = "   ", weeklyGoalSessions = null)
        }
    }

    "updateProfile에 21자 이상 nickname을 주면 예외를 던진다" {
        val user = newUser()

        shouldThrow<IllegalArgumentException> {
            user.updateProfile(nickname = "가".repeat(21), weeklyGoalSessions = null)
        }
    }

    "updateProfile에 weeklyGoalSessions 0을 주면 예외를 던진다" {
        val user = newUser()

        shouldThrow<IllegalArgumentException> {
            user.updateProfile(nickname = null, weeklyGoalSessions = 0)
        }
    }

    "updateProfile에 weeklyGoalSessions 8을 주면 예외를 던진다" {
        val user = newUser()

        shouldThrow<IllegalArgumentException> {
            user.updateProfile(nickname = null, weeklyGoalSessions = 8)
        }
    }
})
