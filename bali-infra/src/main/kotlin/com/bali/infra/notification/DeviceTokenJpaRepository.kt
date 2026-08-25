package com.bali.infra.notification

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface DeviceTokenJpaRepository : JpaRepository<DeviceTokenJpaEntity, UUID> {
    fun findByExpoPushToken(expoPushToken: String): DeviceTokenJpaEntity?
    fun findAllByUserId(userId: UUID): List<DeviceTokenJpaEntity>
    fun deleteByExpoPushToken(expoPushToken: String)
    fun deleteByUserIdAndExpoPushToken(userId: UUID, expoPushToken: String)
}
