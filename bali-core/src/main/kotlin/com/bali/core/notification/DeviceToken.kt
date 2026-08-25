package com.bali.core.notification

import java.time.Instant
import java.util.UUID

// 유저가 등록한 Expo push token. 재설치 시 동일 토큰이 다른 유저로 넘어올 수 있어 토큰 자체가 유니크 키
data class DeviceToken(
    val id: UUID?,
    val userId: UUID,
    val expoPushToken: String,
    val platform: DevicePlatform,
    val createdAt: Instant,
    val updatedAt: Instant,
)
