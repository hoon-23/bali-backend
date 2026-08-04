package com.bali.api.auth

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

// JWT 토큰 헤더를 파싱하고 SecurityContext에 인증 정보를 설정하는 필터의 동작을 검증
class JwtAuthenticationFilterTest {

    private val jwtTokenProvider = JwtTokenProvider(
        secret = "test-secret-key-must-be-at-least-32-bytes-long!!",
        expirationMillis = 3600_000,
    )
    private val filter = JwtAuthenticationFilter(jwtTokenProvider)

    // 유효한 Bearer 토큰이 있으면 SecurityContext에 인증된 사용자 ID가 설정되는지 확인
    @Test
    fun `valid bearer token sets the authenticated user id in the security context`() {
        SecurityContextHolder.clearContext()
        val userId = UUID.randomUUID()
        val token = jwtTokenProvider.generateToken(userId, "test@example.com")

        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer $token")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertEquals(userId.toString(), SecurityContextHolder.getContext().authentication?.name)
    }

    // Authorization 헤더가 없으면 SecurityContext가 비어있는 채로 유지되는지 확인
    @Test
    fun `missing header leaves security context empty`() {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    // 유효하지 않은 토큰이면 SecurityContext가 비어있는 채로 유지되는지 확인
    @Test
    fun `invalid token leaves security context empty`() {
        SecurityContextHolder.clearContext()
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer garbage")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
