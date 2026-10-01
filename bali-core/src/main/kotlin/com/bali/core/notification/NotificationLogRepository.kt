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

    // 기준 시각(since) 이후 알림을 sentAt 내림차순으로 페이지 조회
    fun findPageByUserIdSentAfter(userId: UUID, since: Instant, page: Int, size: Int): NotificationLogPage

    // 기준 시각 이후의 안 읽은 알림 개수
    fun countUnreadByUserIdSentAfter(userId: UUID, since: Instant): Long

    // 본인 소유 알림을 읽음 처리. 소유가 아니거나 없으면 false, 이미 읽었으면 기존 read_at을 유지하고 true
    fun markRead(userId: UUID, id: UUID, at: Instant): Boolean

    // 본인 안 읽은 알림 전체를 읽음 처리하고 갱신 건수를 반환
    fun markAllRead(userId: UUID, at: Instant): Int
}

// 알림함 페이지 조회 결과
data class NotificationLogPage(val items: List<NotificationLog>, val totalElements: Long)
