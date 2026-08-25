package com.bali.batch.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// 유저 1명에게 알림을 발송하고 결과를 처리하는 공용 헬퍼. 모든 XxxRunner가 공유한다.
// DeviceNotRegistered 토큰은 즉시 삭제하고, 발송 성공(티켓 접수) 시에만 notification_log에 기록한다.
// 네트워크 등 예외는 그대로 던진다 - 로그를 안 남겨야 Airflow 재시도 시 자연스럽게 재발송된다
@Component
class NotificationDispatcher(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val notificationSender: NotificationSender,
) {
    fun dispatch(userId: UUID, type: NotificationType, referenceId: UUID?, title: String, body: String) {
        val tokens = deviceTokenRepository.findAllByUserId(userId)
        if (tokens.isEmpty()) return

        val results = notificationSender.send(tokens.map { PushMessage(token = it.expoPushToken, title = title, body = body) })

        results.filter { it.error == PushSendError.DEVICE_NOT_REGISTERED }
            .forEach { deviceTokenRepository.deleteByToken(it.token) }

        val firstSuccess = results.firstOrNull { it.ticketId != null } ?: return
        notificationLogRepository.save(
            NotificationLog(
                id = null, userId = userId, type = type, referenceId = referenceId,
                expoTicketId = firstSuccess.ticketId, deliveryStatus = DeliveryStatus.PENDING,
                deliveryError = null, sentAt = Instant.now(),
            )
        )
    }
}
