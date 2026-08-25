package com.bali.core.notification

import java.time.Instant
import java.util.UUID

// 알림 발송 이력 영속성을 위한 포트 인터페이스
interface NotificationLogRepository {
    // ROUTINE_REMINDER/WEEKLY_SUMMARY/MONTHLY_SUMMARY 중복 발송 방지: 동일 (type, referenceId) 발송 여부
    fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean

    // INACTIVITY_ALERT 쿨다운 체크: after 시각 이후 동일 (userId, type) 발송 여부
    fun existsByUserIdAndTypeSentAfter(userId: UUID, type: NotificationType, after: Instant): Boolean

    fun save(log: NotificationLog): NotificationLog
}
