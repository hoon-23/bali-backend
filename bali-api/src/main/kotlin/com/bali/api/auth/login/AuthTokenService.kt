package com.bali.api.auth.login

import com.bali.api.auth.jwt.TokenIssuer
import com.bali.api.auth.jwt.TokenPair
import com.bali.core.auth.RefreshTokenRepository
import com.bali.core.user.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant

// 발급된 refresh token으로 토큰을 재발급하거나 폐기(로그아웃)하는 서비스
@Service
class AuthTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val tokenIssuer: TokenIssuer,
) {
    // refresh token을 검증한 뒤 기존 토큰은 폐기(rotation)하고 새 access/refresh token 쌍을 발급한다
    fun refresh(rawRefreshToken: String): TokenPair {
        val existing = refreshTokenRepository.findByTokenHash(TokenIssuer.hash(rawRefreshToken))
            ?: throw InvalidRefreshTokenException("유효하지 않은 refresh token 입니다")
        if (!existing.isValid(Instant.now())) {
            throw InvalidRefreshTokenException("만료되었거나 이미 폐기된 refresh token 입니다")
        }
        refreshTokenRepository.revoke(existing.id!!)
        val user = userRepository.findById(existing.userId)
            ?: throw InvalidRefreshTokenException("사용자를 찾을 수 없습니다")
        return tokenIssuer.issue(user.id!!, user.email)
    }

    // refresh token을 폐기한다 (로그아웃). 이미 없거나 폐기된 토큰이어도 멱등하게 성공 처리
    fun logout(rawRefreshToken: String) {
        val existing = refreshTokenRepository.findByTokenHash(TokenIssuer.hash(rawRefreshToken)) ?: return
        refreshTokenRepository.revoke(existing.id!!)
    }
}
