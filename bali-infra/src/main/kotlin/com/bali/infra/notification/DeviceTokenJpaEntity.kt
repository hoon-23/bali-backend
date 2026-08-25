package com.bali.infra.notification

import com.bali.core.notification.DevicePlatform
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

// DeviceToken 도메인 모델 영속성을 위한 JPA 엔티티
@Entity
@Table(name = "device_tokens")
class DeviceTokenJpaEntity(
    @Id
    var id: UUID = UUID.randomUUID(),
    var userId: UUID = UUID.randomUUID(),
    var expoPushToken: String = "",
    @Enumerated(EnumType.STRING)
    var platform: DevicePlatform = DevicePlatform.IOS,
    var createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
)
