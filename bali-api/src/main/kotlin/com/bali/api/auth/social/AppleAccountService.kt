package com.bali.api.auth.social

import com.bali.core.auth.AppleRefreshTokenRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

// Apple 로그인 사용자의 Apple refresh token 보관과 탈퇴 시 revoke를 담당한다.
// 어느 단계든 실패해도 로그인/탈퇴 자체를 막지 않는다(탈퇴는 항상 완료되어야 하므로 로그만 남김)
@Service
class AppleAccountService(
    private val appleAuthClient: AppleAuthClient,
    private val appleRefreshTokenRepository: AppleRefreshTokenRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 로그인 직후 authorizationCode를 refresh token으로 교환해 저장한다. 코드가 없으면(구버전 앱) 건너뛴다
    fun storeRefreshToken(userId: UUID, authorizationCode: String?) {
        if (authorizationCode.isNullOrBlank()) return
        val refreshToken = appleAuthClient.exchangeCode(authorizationCode) ?: return
        appleRefreshTokenRepository.save(userId, refreshToken)
    }

    // 탈퇴 시 저장된 Apple refresh token을 revoke하고 삭제한다. 저장된 토큰이 없으면 건너뛴다
    fun revokeOnWithdraw(userId: UUID) {
        val refreshToken = appleRefreshTokenRepository.findByUserId(userId) ?: return
        if (!appleAuthClient.revoke(refreshToken)) {
            log.error("Apple revoke 실패, 저장된 토큰은 삭제하고 탈퇴를 계속 진행 userId={}", userId)
        }
        appleRefreshTokenRepository.deleteByUserId(userId)
    }
}
