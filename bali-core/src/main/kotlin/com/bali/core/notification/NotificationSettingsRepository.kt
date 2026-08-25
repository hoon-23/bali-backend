package com.bali.core.notification

import java.util.UUID

// 알림 설정 영속성을 위한 포트 인터페이스
interface NotificationSettingsRepository {
    // 행이 없으면 null (호출부가 NotificationSettings.defaults(userId)로 취급)
    fun findByUserId(userId: UUID): NotificationSettings?
    fun save(settings: NotificationSettings): NotificationSettings
}
