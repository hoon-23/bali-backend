package com.bali.api.auth.login

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.api.auth.jwt.TokenIssuer
import com.bali.api.auth.social.SocialTokenVerifier
import com.bali.api.auth.social.SocialUserInfo
import com.bali.core.auth.RefreshToken
import com.bali.core.auth.RefreshTokenRepository
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class SocialLoginServiceTest {

    private class InMemoryUserRepository : UserRepository {
        private val store = mutableMapOf<UUID, User>()

        override fun findById(id: UUID): User? = store[id]

        override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): User? =
            store.values.find { it.provider == provider && it.providerId == providerId }

        override fun findByEmail(email: String): User? =
            store.values.find { it.email == email }

        override fun save(user: User): User {
            val toSave = user.copy(id = user.id ?: UUID.randomUUID())
            store[toSave.id!!] = toSave
            return toSave
        }

        override fun findAllByStatus(status: UserStatus): List<User> =
            store.values.filter { it.status == status }
    }

    private class InMemoryRefreshTokenRepository : RefreshTokenRepository {
        private val store = mutableMapOf<UUID, RefreshToken>()
        override fun save(token: RefreshToken): RefreshToken {
            val toSave = token.copy(id = token.id ?: UUID.randomUUID())
            store[toSave.id!!] = toSave
            return toSave
        }
        override fun findByTokenHash(tokenHash: String): RefreshToken? =
            store.values.find { it.tokenHash == tokenHash }
        override fun revokeIfActive(id: UUID): Boolean {
            val token = store[id] ?: return false
            if (token.revoked) return false
            store[id] = token.copy(revoked = true)
            return true
        }
    }

    private class FakeVerifier(
        override val provider: AuthProvider,
        private val infoByToken: Map<String, SocialUserInfo>,
    ) : SocialTokenVerifier {
        override fun verify(token: String): SocialUserInfo =
            infoByToken[token] ?: throw IllegalArgumentException("알 수 없는 토큰: $token")
    }

    private fun newTokenIssuer() = TokenIssuer(
        JwtTokenProvider(secret = "test-secret-key-must-be-at-least-32-bytes-long!!", expirationMillis = 3600_000),
        InMemoryRefreshTokenRepository(),
        refreshExpirationMillis = 2_592_000_000L,
    )

    @Test
    fun `최초 로그인이면 신규 유저를 생성한다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.GOOGLE,
            mapOf("token-1" to SocialUserInfo(providerId = "google-sub-1", email = "a@example.com")),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, newTokenIssuer())

        val tokens = service.login(AuthProvider.GOOGLE, "token-1", email = null)

        assertNotNull(tokens.accessToken)
        assertNotNull(tokens.refreshToken)
        val saved = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-1")
        assertEquals("a@example.com", saved?.email)
    }

    @Test
    fun `기존 유저가 재로그인하면 같은 유저의 토큰을 발급한다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.GOOGLE,
            mapOf("token-1" to SocialUserInfo(providerId = "google-sub-2", email = "b@example.com")),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, newTokenIssuer())

        service.login(AuthProvider.GOOGLE, "token-1", email = null)
        service.login(AuthProvider.GOOGLE, "token-1", email = null)

        assertEquals(1, userRepository.findAllByStatus(UserStatus.ACTIVE).size)
    }

    // withdraw()가 providerId를 파기하므로, 탈퇴 후 같은 provider 계정으로 재로그인하면
    // 기존 계정과 매칭되지 않고 신규 계정이 생성된다 (탈퇴가 실제로 되돌릴 수 없다는 의도된 부수효과)
    @Test
    fun `탈퇴한 유저가 재로그인하면 신규 계정이 생성된다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.GOOGLE,
            mapOf("token-1" to SocialUserInfo(providerId = "google-sub-withdraw", email = "c@example.com")),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, newTokenIssuer())
        service.login(AuthProvider.GOOGLE, "token-1", email = null)
        val original = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-withdraw")!!
        userRepository.save(original.withdraw())

        service.login(AuthProvider.GOOGLE, "token-1", email = null)

        // 재로그인 시 providerId가 다시 "google-sub-withdraw"로 들어오지만, 파기된 기존 계정의
        // providerId는 이미 "withdrawn-{id}"로 바뀌어 있어 매칭되지 않고 새 계정이 생성된다
        val recreated = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-withdraw")
        assertNotNull(recreated)
        assertTrue(recreated?.id != original.id)
        assertEquals(UserStatus.ACTIVE, recreated?.status)
        val oldAccount = userRepository.findById(original.id!!)
        assertEquals(UserStatus.WITHDRAWN, oldAccount?.status)
    }

    @Test
    fun `Apple 최초 로그인은 요청 바디 email을 사용한다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.APPLE,
            mapOf("apple-token" to SocialUserInfo(providerId = "apple-sub-1", email = null)),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, newTokenIssuer())

        service.login(AuthProvider.APPLE, "apple-token", email = "apple-user@example.com")

        val saved = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-1")
        assertEquals("apple-user@example.com", saved?.email)
    }

    @Test
    fun `email을 못 얻은 최초 로그인은 placeholder 이메일로 생성된다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.APPLE,
            mapOf("apple-token" to SocialUserInfo(providerId = "apple-sub-2", email = null)),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, newTokenIssuer())

        service.login(AuthProvider.APPLE, "apple-token", email = null)

        val saved = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-2")
        assertTrue(saved?.email == "apple-sub-2@apple.bali.internal")
    }

    @Test
    fun `최초 로그인이면 nickname이 자동 생성된다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.GOOGLE,
            mapOf("token-nick" to SocialUserInfo(providerId = "google-sub-nick", email = "nick@example.com")),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, newTokenIssuer())

        service.login(AuthProvider.GOOGLE, "token-nick", email = null)

        val saved = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, "google-sub-nick")
        assertTrue(saved?.nickname?.isNotBlank() == true)
    }
}
