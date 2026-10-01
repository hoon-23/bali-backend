package com.bali.infra.notification

import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogPage
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
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
                title = log.title, body = log.body, readAt = log.readAt,
            )
        ).toDomain()

    private fun NotificationLogJpaEntity.toDomain() = NotificationLog(
        id = id, userId = userId, type = type, referenceId = referenceId, expoTicketId = expoTicketId,
        deliveryStatus = deliveryStatus, deliveryError = deliveryError, sentAt = sentAt,
        title = title, body = body, readAt = readAt,
    )

    override fun findPageByUserIdSentAfter(userId: UUID, since: Instant, page: Int, size: Int): NotificationLogPage {
        val result = jpaRepository.findByUserIdAndSentAtAfter(userId, since, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt")))
        return NotificationLogPage(items = result.content.map { it.toDomain() }, totalElements = result.totalElements)
    }

    override fun countUnreadByUserIdSentAfter(userId: UUID, since: Instant): Long =
        jpaRepository.countByUserIdAndSentAtAfterAndReadAtIsNull(userId, since)

    @Transactional
    override fun markRead(userId: UUID, id: UUID, at: Instant): Boolean {
        val entity = jpaRepository.findByIdAndUserId(id, userId) ?: return false
        if (entity.readAt == null) entity.readAt = at
        return true
    }

    @Transactional
    override fun markAllRead(userId: UUID, at: Instant): Int = jpaRepository.markAllRead(userId, at)
}
