package com.bali.api.notification

import com.bali.api.auth.currentUserId
import com.bali.core.notification.NotificationLog
import com.bali.core.notification.NotificationLogRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

// 서버가 보낸 푸시 알림함(목록/안 읽은 개수/읽음 처리) API 엔드포인트를 처리하는 REST 컨트롤러
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notification Inbox", description = "알림함 API")
class NotificationInboxController(
    private val logRepository: NotificationLogRepository,
) {
    companion object {
        private const val MAX_SIZE = 50
    }

    // 알림함 노출 기준 시각(최근 30일)
    private fun windowStart(): Instant = Instant.now().minus(NotificationLog.INBOX_WINDOW_DAYS, ChronoUnit.DAYS)

    // 최근 30일 알림 목록 (sentAt 내림차순). page<0은 0, size는 1..50으로 보정
    @Operation(summary = "알림함 목록 조회", description = "최근 30일 알림을 최신순으로 조회한다. size 기본 20, 최대 50")
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): NotificationPageResponse {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, MAX_SIZE)
        val result = logRepository.findPageByUserIdSentAfter(currentUserId(), windowStart(), safePage, safeSize)
        return NotificationPageResponse(
            items = result.items.map { NotificationItemResponse.from(it) },
            page = safePage, size = safeSize, totalElements = result.totalElements,
            hasNext = (safePage + 1).toLong() * safeSize < result.totalElements,
        )
    }

    // 안 읽은 알림 개수 (최근 30일)
    @Operation(summary = "안 읽은 알림 개수", description = "최근 30일 중 안 읽은 알림 개수")
    @GetMapping("/unread-count")
    fun unreadCount(): UnreadCountResponse =
        UnreadCountResponse(logRepository.countUnreadByUserIdSentAfter(currentUserId(), windowStart()))

    // 알림 1건 읽음 처리. 본인 소유가 아니거나 없으면 404(존재 노출 방지)
    @Operation(summary = "알림 읽음 처리", description = "본인 소유가 아니거나 없으면 404, 이미 읽었으면 최초 읽은 시각을 유지한다")
    @PatchMapping("/{id}/read")
    fun markRead(@PathVariable id: UUID): ResponseEntity<Void> =
        if (logRepository.markRead(currentUserId(), id, Instant.now())) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()

    // 본인 안 읽은 알림 전체 읽음 처리
    @Operation(summary = "알림 전체 읽음 처리", description = "본인의 안 읽은 알림을 모두 읽음 처리한다")
    @PostMapping("/read-all")
    fun readAll(): ResponseEntity<Void> {
        logRepository.markAllRead(currentUserId(), Instant.now())
        return ResponseEntity.noContent().build()
    }
}
