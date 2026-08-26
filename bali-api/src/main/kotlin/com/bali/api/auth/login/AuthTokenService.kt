package com.bali.api.auth.login

import com.bali.api.auth.jwt.TokenIssuer
import com.bali.api.auth.jwt.TokenPair
import com.bali.core.auth.RefreshTokenRepository
import com.bali.core.user.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

// 발급된 refresh token으로 토큰을 재발급하거나 폐기(로그아웃)하는 서비스
@Service
class AuthTokenService(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val userRepository: UserRepository,
    private val tokenIssuer: TokenIssuer,
) {
    // refresh token을 검증한 뒤 기존 토큰은 폐기(rotation)하고 새 access/refresh token 쌍을 발급한다.
    // 폐기와 신규 발급을 하나의 트랜잭션으로 묶어, 발급 단계에서 실패하더라도 폐기가 롤백되어
    // 클라이언트가 들고 있는 토큰이 헛되이 죽지 않도록 한다. revokeIfActive가 false를 반환하면
    // (동시에 같은 토큰으로 들어온 다른 요청이 먼저 rotation을 마친 경우) 즉시 실패 처리한다
    @Transactional
    fun refresh(rawRefreshToken: String): TokenPair {
        val existing = refreshTokenRepository.findByTokenHash(TokenIssuer.hash(rawRefreshToken))
            ?: throw InvalidRefreshTokenException("유효하지 않은 refresh token 입니다")
        if (!existing.isValid(Instant.now())) {
            throw InvalidRefreshTokenException("만료되었거나 이미 폐기된 refresh token 입니다")
        }
        if (!refreshTokenRepository.revokeIfActive(existing.id!!)) {
            throw InvalidRefreshTokenException("이미 사용된 refresh token 입니다")
        }
        val user = userRepository.findById(existing.userId)
            ?: throw InvalidRefreshTokenException("사용자를 찾을 수 없습니다")
        return tokenIssuer.issue(user.id!!, user.email)
    }

    // refresh token을 폐기한다 (로그아웃). 이미 없거나 폐기된 토큰이어도 멱등하게 성공 처리
    @Transactional
    fun logout(rawRefreshToken: String) {
        val existing = refreshTokenRepository.findByTokenHash(TokenIssuer.hash(rawRefreshToken)) ?: return
        refreshTokenRepository.revokeIfActive(existing.id!!)
    }
}
