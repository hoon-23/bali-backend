package com.bali.api.auth.login

import com.bali.api.auth.jwt.JwtTokenProvider
import com.bali.api.auth.jwt.TokenIssuer
import com.bali.api.auth.social.SocialProviderUnavailableException
import com.bali.api.auth.social.SocialTokenVerifier
import com.bali.api.auth.social.SocialUserInfo
import com.bali.api.common.ApiExceptionHandler
import com.bali.core.auth.RefreshToken
import com.bali.core.auth.RefreshTokenRepository
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import com.jayway.jsonpath.JsonPath
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

    private class InMemoryRefreshTokenRepository : RefreshTokenRepository {
        private val store = mutableMapOf<UUID, RefreshToken>()
        override fun save(token: RefreshToken): RefreshToken {
            val toSave = token.copy(id = token.id ?: UUID.randomUUID())
            store[toSave.id!!] = toSave
            return toSave
        }
        override fun findByTokenHash(tokenHash: String): RefreshToken? =
            store.values.find { it.tokenHash == tokenHash }
        override fun revoke(id: UUID) {
            store[id]?.let { store[id] = it.copy(revoked = true) }
        }
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
    private lateinit var userRepository: InMemoryUserRepository
    private lateinit var refreshTokenRepository: InMemoryRefreshTokenRepository

    private fun buildMockMvc(verifiers: List<SocialTokenVerifier>): MockMvc {
        val tokenIssuer = TokenIssuer(
            JwtTokenProvider(secret = "test-secret-key-must-be-at-least-32-bytes-long!!", expirationMillis = 3600_000),
            refreshTokenRepository,
            refreshExpirationMillis = 2_592_000_000L,
        )
        val loginService = SocialLoginService(verifiers, userRepository, tokenIssuer)
        val tokenService = AuthTokenService(refreshTokenRepository, userRepository, tokenIssuer)
        return MockMvcBuilders.standaloneSetup(AuthController(loginService, tokenService))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @BeforeEach
    fun setUp() {
        userRepository = InMemoryUserRepository()
        refreshTokenRepository = InMemoryRefreshTokenRepository()
        mockMvc = buildMockMvc(
            listOf(FakeVerifier(AuthProvider.GOOGLE, "valid-google-token", SocialUserInfo("google-sub-1", "a@example.com")))
        )
    }

    @Test
    fun `유효한 토큰으로 로그인하면 200과 accessToken, refreshToken을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "valid-google-token"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
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
        val unavailableMockMvc = buildMockMvc(listOf(AlwaysUnavailableVerifier(AuthProvider.NAVER)))

        unavailableMockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "NAVER", "token": "any-token"}""")
        )
            .andExpect(status().isBadGateway)
    }

    @Test
    fun `로그인 후 발급된 refreshToken으로 재발급하면 200과 새 토큰 쌍을 반환한다`() {
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "valid-google-token"}""")
        ).andReturn()
        val refreshToken = JsonPath.read<String>(loginResult.response.contentAsString, "$.refreshToken")

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.refreshToken").exists())
    }

    @Test
    fun `재발급에 사용된 refreshToken은 폐기되어 재사용하면 401을 반환한다`() {
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "valid-google-token"}""")
        ).andReturn()
        val refreshToken = JsonPath.read<String>(loginResult.response.contentAsString, "$.refreshToken")

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `존재하지 않는 refreshToken으로 재발급하면 401을 반환한다`() {
        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "no-such-token"}""")
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `유효한 refreshToken으로 로그아웃하면 204를 반환하고 이후 재발급에 사용할 수 없다`() {
        val loginResult = mockMvc.perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"provider": "GOOGLE", "token": "valid-google-token"}""")
        ).andReturn()
        val refreshToken = JsonPath.read<String>(loginResult.response.contentAsString, "$.refreshToken")

        mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "$refreshToken"}""")
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `존재하지 않는 refreshToken으로 로그아웃해도 204를 반환한다 (멱등)`() {
        mockMvc.perform(
            post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken": "no-such-token"}""")
        )
            .andExpect(status().isNoContent)
    }
}
