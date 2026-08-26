package com.bali.batch.notification

import com.bali.batch.runResiliently
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 매일 1회 실행되어, 마지막으로 완료된 세션 로그가 7일 이상 지난 ACTIVE 유저에게 이탈 알림을 보낸다.
// 운동 기록이 아예 없는 유저는 대상에서 제외한다 (첫 운동 유도는 별도 온보딩 알림의 몫)
@Component
class InactivityAlertRunner(
    private val userRepository: UserRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val INACTIVITY_THRESHOLD_DAYS = 7L
        private const val TITLE = "오랜만이에요"
        private const val BODY = "7일째 운동 기록이 없어요. 오늘 가볍게 시작해볼까요?"
    }

    private val log = LoggerFactory.getLogger(InactivityAlertRunner::class.java)

    fun run(): Int {
        val today = LocalDate.now(APP_ZONE)
        return runResiliently(
            items = userRepository.findAllByStatus(UserStatus.ACTIVE),
            process = { user -> processUser(user, today) },
            onFailure = { user, e -> log.error("이탈 알림 발송 실패: userId=${user.id}", e) },
        )
    }

    private fun processUser(user: User, today: LocalDate) {
        val userId = user.id!!
        val settings = settingsRepository.findByUserId(userId)
        if (settings?.inactivityAlertEnabled == false) return

        val lastActiveDate = sessionRepository.findLastActiveDate(userId) ?: return
        if (ChronoUnit.DAYS.between(lastActiveDate, today) < INACTIVITY_THRESHOLD_DAYS) return

        val cooldownStart = Instant.now().minus(INACTIVITY_THRESHOLD_DAYS, ChronoUnit.DAYS)
        if (notificationLogRepository.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, cooldownStart)) return

        dispatcher.dispatch(userId, NotificationType.INACTIVITY_ALERT, referenceId = null, TITLE, BODY)
    }
}
