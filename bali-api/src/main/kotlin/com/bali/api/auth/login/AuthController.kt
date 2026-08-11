package com.bali.api.auth.login

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// 소셜 로그인 관련 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "소셜 로그인 인증 API")
class AuthController(
    private val socialLoginService: SocialLoginService,
) {
    // provider 토큰을 검증해 로그인/가입 처리 후 자체 JWT를 발급한다
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: SocialLoginRequest): ResponseEntity<Map<String, String>> {
        val accessToken = socialLoginService.login(request.provider, request.token, request.email)
        return ResponseEntity.ok(mapOf("accessToken" to accessToken))
    }
}
