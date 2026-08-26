package com.bali.api.auth.login

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.api.auth.jwt.TokenIssuer
import com.bali.core.auth.RefreshToken
import com.bali.core.auth.RefreshTokenRepository
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class AuthTokenServiceTest {

    private class SingleUserRepository(private val user: User) : UserRepository {
        override fun findById(id: UUID): User? = if (id == user.id) user else null
        override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? = null
        override fun findByEmail(email: String): User? = if (email == user.email) user else null
        override fun save(user: User): User = user
        override fun findAllByStatus(status: UserStatus): List<User> = emptyList()
    }

    // revokeIfActive가 항상 false를 반환하는 fake. 동시에 같은 refresh token으로 들어온 다른 요청이
    // 먼저 rotation을 마쳐버려 이 요청이 race에서 진 상황을 흉내낸다
    private class AlwaysLosesRaceRefreshTokenRepository(private val existing: RefreshToken) : RefreshTokenRepository {
        val savedTokens = mutableListOf<RefreshToken>()
        override fun save(token: RefreshToken): RefreshToken {
            val toSave = token.copy(id = token.id ?: UUID.randomUUID())
            savedTokens += toSave
            return toSave
        }
        override fun findByTokenHash(tokenHash: String): RefreshToken? =
            if (tokenHash == existing.tokenHash) existing else null
        override fun revokeIfActive(id: UUID): Boolean = false
    }

    @Test
    fun `동시 rotation에서 진 refresh token은 예외를 던지고 새 토큰을 발급하지 않는다`() {
        val userId = UUID.randomUUID()
        val user = User(
            id = userId, email = "race@example.com", provider = AuthProvider.GOOGLE,
            providerId = "google-sub-race", status = UserStatus.ACTIVE, createdAt = Instant.now(),
        )
        val existingToken = RefreshToken(
            id = UUID.randomUUID(), userId = userId, tokenHash = TokenIssuer.hash("raw-token"),
            expiresAt = Instant.now().plusSeconds(3600),
        )
        val refreshTokenRepository = AlwaysLosesRaceRefreshTokenRepository(existingToken)
        val tokenIssuer = TokenIssuer(
            JwtTokenProvider(secret = "test-secret-key-must-be-at-least-32-bytes-long!!", expirationMillis = 3600_000),
            refreshTokenRepository,
            refreshExpirationMillis = 2_592_000_000L,
        )
        val service = AuthTokenService(refreshTokenRepository, SingleUserRepository(user), tokenIssuer)

        assertThrows(InvalidRefreshTokenException::class.java) { service.refresh("raw-token") }
        assertEquals(0, refreshTokenRepository.savedTokens.size)
    }
}
