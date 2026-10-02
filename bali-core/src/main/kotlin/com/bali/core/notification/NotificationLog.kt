package com.bali.core.notification

import java.time.Instant
import java.util.UUID

// 알림 발송 이력. 중복 발송 방지(idempotency)와 발송 기록 조회를 겸한다
data class NotificationLog(
    val id: UUID?,
    val userId: UUID,
    val type: NotificationType,
    val referenceId: UUID?,
    val expoTicketId: String?,
    val deliveryStatus: DeliveryStatus,
    val deliveryError: String?,
    val sentAt: Instant,
    val title: String = "",
    val body: String = "",
    val readAt: Instant? = null,
) {
    companion object {
        // 알림함 노출 기간(일). 알림함 API와 푸시 아이콘 뱃지가 같은 기준을 쓴다
        const val INBOX_WINDOW_DAYS = 30L
    }
}
