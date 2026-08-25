package com.bali.infra.notification

import com.bali.core.notification.NotificationSettings
import com.bali.core.notification.NotificationSettingsRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// JPA 백엔드로 NotificationSettingsRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class NotificationSettingsRepositoryAdapter(
    private val jpaRepository: NotificationSettingsJpaRepository,
) : NotificationSettingsRepository {

    override fun findByUserId(userId: UUID): NotificationSettings? =
        jpaRepository.findById(userId).orElse(null)?.toDomain()

    @Transactional
    override fun save(settings: NotificationSettings): NotificationSettings =
        jpaRepository.save(
            NotificationSettingsJpaEntity(
                userId = settings.userId,
                routineReminderEnabled = settings.routineReminderEnabled,
                inactivityAlertEnabled = settings.inactivityAlertEnabled,
                summaryNotificationEnabled = settings.summaryNotificationEnabled,
            )
        ).toDomain()

    private fun NotificationSettingsJpaEntity.toDomain() = NotificationSettings(
        userId = userId, routineReminderEnabled = routineReminderEnabled,
        inactivityAlertEnabled = inactivityAlertEnabled, summaryNotificationEnabled = summaryNotificationEnabled,
    )
}
