package com.bali.infra.notification

import com.bali.core.notification.NotificationType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface NotificationLogJpaRepository : JpaRepository<NotificationLogJpaEntity, UUID> {
    fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean
    fun existsByUserIdAndTypeAndSentAtAfter(userId: UUID, type: NotificationType, sentAt: Instant): Boolean
    fun findByUserIdAndSentAtAfter(userId: UUID, sentAt: Instant, pageable: Pageable): Page<NotificationLogJpaEntity>
    fun countByUserIdAndSentAtAfterAndReadAtIsNull(userId: UUID, sentAt: Instant): Long
    fun findByIdAndUserId(id: UUID, userId: UUID): NotificationLogJpaEntity?

    @Modifying(clearAutomatically = true)
    @Query("UPDATE NotificationLogJpaEntity n SET n.readAt = :at WHERE n.userId = :userId AND n.readAt IS NULL")
    fun markAllRead(@Param("userId") userId: UUID, @Param("at") at: Instant): Int
}
