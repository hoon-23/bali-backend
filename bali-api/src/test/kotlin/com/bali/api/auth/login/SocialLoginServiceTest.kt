package com.bali.api.auth.login

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.api.auth.social.SocialTokenVerifier
import com.bali.api.auth.social.SocialUserInfo
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

        override fun save(user: User): User {
            val toSave = user.copy(id = user.id ?: UUID.randomUUID())
            store[toSave.id!!] = toSave
            return toSave
        }

        override fun findAllByStatus(status: UserStatus): List<User> =
            store.values.filter { it.status == status }
    }

    private class FakeVerifier(
        override val provider: AuthProvider,
        private val infoByToken: Map<String, SocialUserInfo>,
    ) : SocialTokenVerifier {
        override fun verify(token: String): SocialUserInfo =
            infoByToken[token] ?: throw IllegalArgumentException("알 수 없는 토큰: $token")
    }

    private val jwtTokenProvider = JwtTokenProvider(
        secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
        expirationMillis = 3600_000,
    )

    @Test
    fun `최초 로그인이면 신규 유저를 생성한다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.GOOGLE,
            mapOf("token-1" to SocialUserInfo(providerId = "google-sub-1", email = "a@example.com")),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, jwtTokenProvider)

        val accessToken = service.login(AuthProvider.GOOGLE, "token-1", email = null)

        assertNotNull(accessToken)
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
        val service = SocialLoginService(listOf(verifier), userRepository, jwtTokenProvider)

        service.login(AuthProvider.GOOGLE, "token-1", email = null)
        service.login(AuthProvider.GOOGLE, "token-1", email = null)

        assertEquals(1, userRepository.findAllByStatus(UserStatus.ACTIVE).size)
    }

    @Test
    fun `Apple 최초 로그인은 요청 바디 email을 사용한다`() {
        val userRepository = InMemoryUserRepository()
        val verifier = FakeVerifier(
            AuthProvider.APPLE,
            mapOf("apple-token" to SocialUserInfo(providerId = "apple-sub-1", email = null)),
        )
        val service = SocialLoginService(listOf(verifier), userRepository, jwtTokenProvider)

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
        val service = SocialLoginService(listOf(verifier), userRepository, jwtTokenProvider)

        service.login(AuthProvider.APPLE, "apple-token", email = null)

        val saved = userRepository.findByProviderAndProviderId(AuthProvider.APPLE, "apple-sub-2")
        assertTrue(saved?.email == "apple-sub-2@apple.bali.internal")
    }
}
