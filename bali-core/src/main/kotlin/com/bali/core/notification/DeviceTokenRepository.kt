package com.bali.core.notification

import java.util.UUID

// 디바이스 토큰 영속성을 위한 포트 인터페이스
interface DeviceTokenRepository {
    // 토큰 문자열 기준 upsert. 동일 토큰이 이미 있으면 userId/platform을 갱신한다
    fun upsert(token: DeviceToken): DeviceToken

    // 특정 유저의 등록된 토큰 전체 목록
    fun findAllByUserId(userId: UUID): List<DeviceToken>

    // Expo가 DeviceNotRegistered를 응답한 토큰을 정리할 때 사용 (유저 무관하게 토큰 기준 삭제)
    fun deleteByToken(token: String)

    // 클라이언트가 명시적으로 로그아웃/앱 삭제 시 호출. 본인 소유 토큰만 삭제되도록 userId도 매칭
    fun deleteByUserIdAndToken(userId: UUID, token: String)
}
