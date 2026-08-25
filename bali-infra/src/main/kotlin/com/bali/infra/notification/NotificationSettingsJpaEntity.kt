package com.bali.infra.notification

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

// NotificationSettings 도메인 모델 영속성을 위한 JPA 엔티티. userId 자체가 PK (1:1)
@Entity
@Table(name = "notification_settings")
class NotificationSettingsJpaEntity(
    @Id
    var userId: UUID = UUID.randomUUID(),
    var routineReminderEnabled: Boolean = true,
    var inactivityAlertEnabled: Boolean = true,
    var summaryNotificationEnabled: Boolean = true,
)
