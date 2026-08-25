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
)
