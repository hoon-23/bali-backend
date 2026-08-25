package com.bali.infra.notification

import com.bali.core.notification.DeliveryStatus
import com.bali.core.notification.NotificationType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// NotificationLog 도메인 모델 영속성을 위한 JPA 엔티티
@Entity
@Table(name = "notification_log")
class NotificationLogJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING)
    var type: NotificationType = NotificationType.ROUTINE_REMINDER,
    var referenceId: UUID? = null,
    var expoTicketId: String? = null,
    @Enumerated(EnumType.STRING)
    var deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    var deliveryError: String? = null,
    var sentAt: Instant = Instant.now(),
)
