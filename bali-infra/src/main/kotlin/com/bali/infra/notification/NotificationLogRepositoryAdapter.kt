package com.bali.infra.notification

import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationType
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// JPA 백엔드로 NotificationLogRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class NotificationLogRepositoryAdapter(
    private val jpaRepository: NotificationLogJpaRepository,
) : NotificationLogRepository {

    override fun existsByTypeAndReferenceId(type: NotificationType, referenceId: UUID): Boolean =
        jpaRepository.existsByTypeAndReferenceId(type, referenceId)

    override fun existsByUserIdAndTypeSentAfter(userId: UUID, type: NotificationType, after: Instant): Boolean =
        jpaRepository.existsByUserIdAndTypeAndSentAtAfter(userId, type, after)

    @Transactional
    override fun save(log: NotificationLog): NotificationLog =
        jpaRepository.save(
            NotificationLogJpaEntity(
                id = log.id ?: UUID.randomUUID(), userId = log.userId, type = log.type,
                referenceId = log.referenceId, expoTicketId = log.expoTicketId,
                deliveryStatus = log.deliveryStatus, deliveryError = log.deliveryError, sentAt = log.sentAt,
            )
        ).toDomain()

    private fun NotificationLogJpaEntity.toDomain() = NotificationLog(
        id = id, userId = userId, type = type, referenceId = referenceId, expoTicketId = expoTicketId,
        deliveryStatus = deliveryStatus, deliveryError = deliveryError, sentAt = sentAt,
    )
}
