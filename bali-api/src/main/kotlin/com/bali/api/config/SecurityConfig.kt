package com.bali.api.config

import com.bali.api.auth.jwt.JwtAuthenticationFilter
import com.bali.api.auth.oauth2.CustomOAuth2UserService
import com.bali.api.auth.oauth2.OAuth2LoginSuccessHandler
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.util.matcher.AntPathRequestMatcher

// Spring Security 설정 - Google OAuth2 로그인 플로우와 인가 규칙을 구성
@Configuration
class SecurityConfig(
    private val customOAuth2UserService: CustomOAuth2UserService,
    private val oAuth2LoginSuccessHandler: OAuth2LoginSuccessHandler,
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val environment: Environment,
) {

    // REST API 엔드포인트의 인증 실패시 401 JSON 응답을 반환하는 엔트리 포인트
    @Bean
    fun restApiAuthenticationEntryPoint(): AuthenticationEntryPoint {
        return AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _: AuthenticationException ->
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("{\"error\": \"Unauthorized\"}")
        }
    }

    // 보안 필터 체인을 정의 - 헬스체크와 OAuth2 경로는 공개, 나머지는 인증 필요
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http {
            // JWT 기반 무상태 API이므로 CSRF 보호는 비활성화
            csrf { disable() }
            // 세션을 사용하지 않고 매 요청마다 JWT로 인증
            sessionManagement { sessionCreationPolicy = org.springframework.security.config.http.SessionCreationPolicy.STATELESS }
            // UsernamePasswordAuthenticationFilter 이전에 JWT 필터를 실행하여 토큰 기반 인증 처리
            addFilterBefore<org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter>(jwtAuthenticationFilter)
            // /api/** 경로의 REST API 요청에 대해서만 401 JSON 응답을 반환하도록 설정
            exceptionHandling {
                defaultAuthenticationEntryPointFor(
                    restApiAuthenticationEntryPoint(),
                    AntPathRequestMatcher("/api/**")
                )
            }
            authorizeHttpRequests {
                authorize("/actuator/health", permitAll)
                authorize("/oauth2/**", permitAll)
                authorize("/login/oauth2/**", permitAll)
                // Swagger 문서는 local/dev에서만 공개, prod는 인증 필요 상태 유지
                if (environment.activeProfiles.any { it in setOf("local", "dev") }) {
                    authorize("/swagger-ui/**", permitAll)
                    authorize("/v3/api-docs/**", permitAll)
                }
                authorize(anyRequest, authenticated)
            }
            oauth2Login {
                // Google scope에 openid가 포함되어 OIDC 프로바이더로 취급되므로
                // (일반 OAuth2용) userService가 아닌 oidcUserService 슬롯에 연결해야 실제로 호출됨
                userInfoEndpoint {
                    oidcUserService = customOAuth2UserService
                }
                // 로그인 성공 시 JWT를 발급하는 핸들러로 응답 작성
                authenticationSuccessHandler = oAuth2LoginSuccessHandler
            }
        }
        return http.build()
    }
}
