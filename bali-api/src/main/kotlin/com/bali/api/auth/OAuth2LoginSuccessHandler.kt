package com.bali.api.auth

import com.bali.core.user.AuthProvider
import com.bali.core.user.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.stereotype.Component

// Google OAuth2 로그인 성공 시 자체 JWT를 발급하여 JSON 응답으로 내려주는 핸들러
@Component
class OAuth2LoginSuccessHandler(
    private val userRepository: UserRepository,
    private val jwtTokenProvider: JwtTokenProvider,
) : AuthenticationSuccessHandler {

    private val objectMapper = ObjectMapper()

    // 인증 성공 시 호출되어 사용자에 대한 JWT를 생성하고 응답 바디에 작성
    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication,
    ) {
        val oAuth2User = authentication.principal as OAuth2User
        val providerId = oAuth2User.getAttribute<String>("sub")!!

        // CustomOAuth2UserService가 이미 사용자를 조회/생성해두었으므로 여기서는 조회만 수행
        val user = userRepository.findByProviderAndProviderId(AuthProvider.GOOGLE, providerId)
            ?: error("User must already be resolved by CustomOAuth2UserService before success handler runs")

        // 사용자 ID와 이메일을 담아 자체 JWT 발급
        val token = jwtTokenProvider.generateToken(user.id!!, user.email)

        // JSON 형태로 accessToken을 응답 바디에 작성
        response.status = HttpServletResponse.SC_OK
        response.contentType = "application/json"
        response.writer.write(objectMapper.writeValueAsString(mapOf("accessToken" to token)))
    }
}
