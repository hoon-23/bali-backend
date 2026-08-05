package com.bali.api.auth.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

// JWT 토큰 생성 및 검증을 담당하는 컴포넌트
@Component
class JwtTokenProvider(
    @Value("\${bali.jwt.secret}") private val secret: String,
    @Value("\${bali.jwt.expiration-millis}") private val expirationMillis: Long,
) {
    // HMAC-SHA 비밀키 생성 (레이지 초기화)
    private val key: SecretKey by lazy { Keys.hmacShaKeyFor(secret.toByteArray()) }

    // 사용자 ID와 이메일을 포함한 JWT 토큰을 생성
    fun generateToken(userId: UUID, email: String): String {
        // 현재 시간 기준으로 발급 및 만료 시간 계산
        val now = Date()
        val expiry = Date(now.time + expirationMillis)

        // JWT 토큰 생성 및 서명
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    // 토큰을 검증하고 사용자 ID를 추출, 유효하지 않으면 null 반환
    fun validateAndGetUserId(token: String): UUID? = try {
        // 토큰 파싱 및 서명 검증
        val claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .payload

        // 토큰의 subject (userId)를 UUID로 변환
        UUID.fromString(claims.subject)
    } catch (ex: Exception) {
        // 모든 예외 (만료, 서명 불일치, 형식 오류 등)를 null로 처리
        null
    }
}
