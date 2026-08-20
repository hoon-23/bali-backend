package com.bali.api.auth.jwt

import com.bali.core.auth.RefreshToken
import com.bali.core.auth.RefreshTokenRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

// access token(JWT) + refresh token(opaque random)을 함께 발급하고, refresh token은 해시로 영속화한다
@Component
class TokenIssuer(
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    @Value("\${bali.jwt.refresh-expiration-millis}") private val refreshExpirationMillis: Long,
) {
    companion object {
        private val secureRandom = SecureRandom()
        private const val RAW_TOKEN_BYTES = 32

        // raw refresh token을 SHA-256으로 해시(hex)한다 - DB에는 원본 대신 이 값만 저장
        fun hash(rawToken: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    // 사용자 ID/이메일로 access token을 발급하고, 새 refresh token을 생성해 해시로 저장한 뒤 원본 쌍을 반환
    fun issue(userId: UUID, email: String): TokenPair {
        val accessToken = jwtTokenProvider.generateToken(userId, email)
        val rawRefreshToken = generateRawToken()
        refreshTokenRepository.save(
            RefreshToken(
                id = null, userId = userId, tokenHash = hash(rawRefreshToken),
                expiresAt = Instant.now().plusMillis(refreshExpirationMillis),
            )
        )
        return TokenPair(accessToken, rawRefreshToken)
    }

    // 암호학적으로 안전한 난수 기반 opaque 토큰 생성 (JWT가 아님 - 예측 불가능성만 필요)
    private fun generateRawToken(): String {
        val bytes = ByteArray(RAW_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
