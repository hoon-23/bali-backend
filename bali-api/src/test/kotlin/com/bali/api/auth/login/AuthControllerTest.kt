package com.bali.api.auth.login

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.api.auth.social.SocialProviderUnavailableException
import com.bali.api.auth.social.SocialTokenVerifier
import com.bali.api.auth.social.SocialUserInfo
import com.bali.api.common.ApiExceptionHandler
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class AuthControllerTest {

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
        private val validToken: String,
        private val info: SocialUserInfo,
    ) : SocialTokenVerifier {
        override fun verify(token: String): SocialUserInfo {
            if (token != validToken) throw IllegalArgumentException("유효하지 않은 토큰입니다")
            return info
        }
    }

    // provider 장애(5xx/연결 실패) 시나리오를 흉내내는 fake. verify() 호출 시 항상
    // SocialProviderUnavailableException을 던진다
    private class AlwaysUnavailableVerifier(override val provider: AuthProvider) : SocialTokenVerifier {
        override fun verify(token: String): SocialUserInfo =
            throw SocialProviderUnavailableException("provider가 응답하지 않습니다")
    }

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val verifiers = listOf(
            FakeVerifier(AuthProvider.GOOGLE, "valid-google-token", SocialUserInfo("google-sub-1", "a@example.com")),
        )
        val service = SocialLoginService(
            verifiers,
            InMemoryUserRepository(),
            JwtTokenProvider(secret = "test-secret-key-must-be-at-least-32-bytes-long!!", expirationMillis = 3600_000),
        )
        mockMvc = MockMvcBuilders.standaloneSetup(AuthController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `유효한 토큰으로 로그인하면 200과 accessToken을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "valid-google-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
    }

    @Test
    fun `검증 실패한 토큰으로 로그인하면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "invalid-token"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `잘못된 provider 값이면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "FACEBOOK", "token": "any-token"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `email 형식이 아닌 값을 요청 바디로 보내면 400을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "valid-google-token", "email": "not-an-email"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `provider 장애로 검증할 수 없으면 502를 반환한다`() {
        val service = SocialLoginService(
            listOf(AlwaysUnavailableVerifier(AuthProvider.NAVER)),
            InMemoryUserRepository(),
            JwtTokenProvider(secret = "test-secret-key-must-be-at-least-32-bytes-long!!", expirationMillis = 3600_000),
        )
        val unavailableMockMvc = MockMvcBuilders.standaloneSetup(AuthController(service))
            .setControllerAdvice(ApiExceptionHandler())
            .build()

        unavailableMockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "NAVER", "token": "any-token"}""")
        )
            .andExpect(status().isBadGateway)
    }
}
