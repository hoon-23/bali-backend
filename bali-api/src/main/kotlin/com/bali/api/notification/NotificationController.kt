package com.bali.api.notification

import com.bali.api.auth.currentUserId
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationSettings
import com.bali.core.notification.NotificationSettingsRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

// 디바이스 토큰 등록/삭제, 알림 유형별 설정 조회/변경 API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification", description = "디바이스 토큰 및 알림 설정 API")
class NotificationController(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val settingsRepository: NotificationSettingsRepository,
) {

    // Expo push token 등록/갱신. 토큰 기준 upsert이므로 재등록해도 안전
    @Operation(summary = "디바이스 토큰 등록", description = "Expo push token을 등록/갱신한다(토큰 기준 upsert)")
    @PostMapping("/device-token")
    fun registerToken(@RequestBody request: DeviceTokenRequest): ResponseEntity<Void> {
        deviceTokenRepository.upsert(
            DeviceToken(
                id = null, userId = currentUserId(), expoPushToken = request.token, platform = request.platform,
                createdAt = Instant.now(), updatedAt = Instant.now(),
            )
        )
        return ResponseEntity.noContent().build()
    }

    // 로그아웃/앱 삭제 시 토큰 제거. 본인 소유 토큰만 삭제된다
    @Operation(summary = "디바이스 토큰 삭제", description = "로그아웃/앱 삭제 시 본인 소유 토큰을 제거한다")
    @DeleteMapping("/device-token")
    fun deleteToken(@RequestBody request: DeviceTokenDeleteRequest): ResponseEntity<Void> {
        deviceTokenRepository.deleteByUserIdAndToken(currentUserId(), request.token)
        return ResponseEntity.noContent().build()
    }

    // 알림 유형별 on/off 설정 조회. 행이 없으면 기본값(전부 true)으로 응답
    @Operation(summary = "알림 설정 조회", description = "행이 없으면 기본값(전부 true)으로 응답한다")
    @GetMapping("/settings")
    fun getSettings(): NotificationSettingsResponse =
        NotificationSettingsResponse.from(settingsRepository.findByUserId(currentUserId()) ?: NotificationSettings.defaults(currentUserId()))

    // 알림 유형별 on/off 설정 변경. null인 필드는 기존 값 유지
    @Operation(summary = "알림 설정 변경", description = "지정한 필드만 변경하고 나머지는 기존 값(없으면 기본값)을 유지한다")
    @PatchMapping("/settings")
    fun patchSettings(@RequestBody request: NotificationSettingsPatchRequest): NotificationSettingsResponse {
        val current = settingsRepository.findByUserId(currentUserId()) ?: NotificationSettings.defaults(currentUserId())
        val updated = current.copy(
            routineReminderEnabled = request.routineReminderEnabled ?: current.routineReminderEnabled,
            inactivityAlertEnabled = request.inactivityAlertEnabled ?: current.inactivityAlertEnabled,
            summaryNotificationEnabled = request.summaryNotificationEnabled ?: current.summaryNotificationEnabled,
        )
        return NotificationSettingsResponse.from(settingsRepository.save(updated))
    }
}
