package com.bali.infra.notification

import com.bali.core.notification.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface NotificationLogJpaRepository : JpaRepository<NotificationLogJpaEntity, UUID> {
    fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean
    fun existsByUserIdAndTypeAndSentAtAfter(userId: UUID, type: NotificationType, sentAt: Instant): Boolean
}
