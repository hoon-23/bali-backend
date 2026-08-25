package com.bali.infra.notification

import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// JPA 백엔드로 DeviceTokenRepository 포트를 구현하는 Spring 리포지토리 어댑터
@Repository
class DeviceTokenRepositoryAdapter(
    private val jpaRepository: DeviceTokenJpaRepository,
) : DeviceTokenRepository {

    @Transactional
    override fun upsert(token: DeviceToken): DeviceToken {
        val existing = jpaRepository.findByExpoPushToken(token.expoPushToken)
        val entity = if (existing != null) {
            existing.userId = token.userId
            existing.platform = token.platform
            existing.updatedAt = Instant.now()
            existing
        } else {
            DeviceTokenJpaEntity(
                id = UUID.randomUUID(), userId = token.userId, expoPushToken = token.expoPushToken,
                platform = token.platform, createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        }
        return jpaRepository.save(entity).toDomain()
    }

    override fun findAllByUserId(userId: UUID): List<DeviceToken> =
        jpaRepository.findAllByUserId(userId).map { it.toDomain() }

    @Transactional
    override fun deleteByToken(token: String) {
        jpaRepository.deleteByExpoPushToken(token)
    }

    @Transactional
    override fun deleteByUserIdAndToken(userId: UUID, token: String) {
        jpaRepository.deleteByUserIdAndExpoPushToken(userId, token)
    }

    private fun DeviceTokenJpaEntity.toDomain() = DeviceToken(
        id = id, userId = userId, expoPushToken = expoPushToken, platform = platform,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}
