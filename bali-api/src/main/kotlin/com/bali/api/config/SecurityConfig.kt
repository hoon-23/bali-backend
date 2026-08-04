package com.bali.api.config

import com.bali.api.auth.CustomOAuth2UserService
import com.bali.api.auth.OAuth2LoginSuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain

// Spring Security 설정 - Google OAuth2 로그인 플로우와 인가 규칙을 구성
@Configuration
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
) {

    // 보안 필터 체인을 정의 - 헬스체크와 OAuth2 경로는 공개, 나머지는 인증 필요
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            // JWT 기반 무상태 API이므로 CSRF 보호는 비활성화
            csrf { disable() }
            // 세션을 사용하지 않고 매 요청마다 JWT로 인증
            sessionManagement { sessionCreationPolicy = org.springframework.security.config.http.SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize("/login/oauth2/**", permitAll)
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                // Google로부터 받은 사용자 정보를 커스텀 서비스로 처리
                userInfoEndpoint {
                    userService = customOAuth2UserService
                }
                // 로그인 성공 시 JWT를 발급하는 핸들러로 응답 작성
                authenticationSuccessHandler = oAuth2LoginSuccessHandler
            }
        }
        return http.build()
    }
}
