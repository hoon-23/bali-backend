package com.bali.api.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

// HTTP 요청의 Authorization 헤더에서 JWT 토큰을 추출하고 검증하여 SecurityContext에 설정
@Component
class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider,
) : OncePerRequestFilter() {

    // 요청에서 Bearer 토큰을 추출하고 검증한 후 SecurityContext에 인증 정보를 설정
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // Authorization 헤더에서 Bearer 토큰 추출
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith("Bearer ")) {
            // Bearer 프리픽스 제거하고 토큰만 추출
            val token = header.removePrefix("Bearer ")
            // JWT 토큰 검증 및 사용자 ID 추출
            val userId = jwtTokenProvider.validateAndGetUserId(token)
            if (userId != null) {
                // 유효한 토큰이면 인증 정보를 SecurityContext에 설정
                val authentication = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
                SecurityContextHolder.getContext().authentication = authentication
            }
        }
        // 필터 체인 진행
        filterChain.doFilter(request, response)
    }
}
