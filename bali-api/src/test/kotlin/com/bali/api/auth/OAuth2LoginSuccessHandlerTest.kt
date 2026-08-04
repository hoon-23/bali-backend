package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.core.user.OAuth2User
import java.time.Instant
import java.util.UUID

// OAuth2LoginSuccessHandler가 JWT 응답을 올바르게 작성하는지 검증하는 테스트
class OAuth2LoginSuccessHandlerTest {

    // 인증 성공 시 응답 바디에 accessToken이 담긴 JSON이 작성되는지 검증
    @Test
    fun `writes a JSON access token for the resolved user`() {
        val userId = UUID.randomUUID()
        val user = User(
            id = userId,
            email = "test@example.com",
            provider = AuthProvider.GOOGLE,
            providerId = "google-sub-1",
            status = UserStatus.ACTIVE,
            createdAt = Instant.now(),
        )

        // 테스트용 UserRepository - 항상 위에서 만든 사용자를 반환
        val userRepository = object : UserRepository {
            override fun findById(id: UUID) = null
            override fun findByProviderAndProviderId(provider: AuthProvider, providerId: String) = user
            override fun save(user: User) = user
        }

        val jwtTokenProvider = JwtTokenProvider(
            secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
            expirationMillis = 3600_000,
        )

        val handler = OAuth2LoginSuccessHandler(userRepository, jwtTokenProvider)

        // Google 로그인 성공 시 principal로 주입될 OAuth2User를 흉내
        val oAuth2User: OAuth2User = DefaultOAuth2User(
            emptyList(),
            mapOf("sub" to "google-sub-1", "email" to "test@example.com"),
            "sub",
        )
        val authentication = TestingAuthenticationToken(oAuth2User, null)

        val response = MockHttpServletResponse()
        handler.onAuthenticationSuccess(
            org.springframework.mock.web.MockHttpServletRequest() as HttpServletRequest,
            response,
            authentication,
        )

        assertEquals(200, response.status)
        assertTrue(response.contentAsString.contains("accessToken"))
    }
}
