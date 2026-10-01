package com.bali.api.notification

import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationType
import java.time.Instant
import java.util.UUID

// 알림함 항목 1건을 HTTP 응답으로 변환하는 DTO
data class NotificationItemResponse(
    val id: UUID,
    val type: NotificationType,
    val title: String,
    val body: String,
    val referenceId: UUID?,
    val sentAt: Instant,
    val read: Boolean,
) {
    companion object {
        // NotificationLog 도메인 모델을 알림함 항목 응답으로 변환
        fun from(log: NotificationLog) = NotificationItemResponse(
            id = log.id!!, type = log.type, title = log.title, body = log.body,
            referenceId = log.referenceId, sentAt = log.sentAt, read = log.readAt != null,
        )
    }
}

// 알림함 목록 페이지 응답 DTO
data class NotificationPageResponse(
    val items: List<NotificationItemResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val hasNext: Boolean,
)

// 안 읽은 알림 개수 응답 DTO
data class UnreadCountResponse(val count: Long)
