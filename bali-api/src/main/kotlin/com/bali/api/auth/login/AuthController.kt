package com.bali.api.auth.login

import com.bali.api.auth.jwt.TokenPair
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 소셜 로그인 및 토큰 재발급/폐기 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "소셜 로그인 인증 API")
class AuthController(
    private val socialLoginService: SocialLoginService,
    private val authTokenService: AuthTokenService,
) {
    // provider 토큰을 검증해 로그인/가입 처리 후 access/refresh 토큰 쌍을 발급한다
    @Operation(summary = "소셜 로그인", description = "provider 토큰을 검증해 로그인/가입 처리 후 access/refresh 토큰 쌍을 발급한다")
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: SocialLoginRequest): ResponseEntity<TokenPair> {
        val tokens = socialLoginService.login(request.provider, request.token, request.email)
        return ResponseEntity.ok(tokens)
    }

    // 유효한 refresh token으로 access/refresh 토큰 쌍을 재발급한다 (기존 refresh token은 폐기됨)
    @Operation(summary = "토큰 재발급", description = "유효한 refresh token으로 access/refresh 토큰 쌍을 재발급한다. 기존 refresh token은 폐기된다")
    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<TokenPair> {
        val tokens = authTokenService.refresh(request.refreshToken)
        return ResponseEntity.ok(tokens)
    }

    // refresh token을 폐기한다 (로그아웃)
    @Operation(summary = "로그아웃", description = "refresh token을 폐기한다")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshRequest) {
        authTokenService.logout(request.refreshToken)
    }
}
