package com.bali.batch.notification

import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

// 하루를 오전(00~12시)/오후(12~24시) 절반 지점(06시, 18시 KST)에 실행되어, 그날 날짜로 아직 시작 안 한
// SCHEDULED 세션이 있는 유저에게 리마인더를 보낸다. sessions 테이블에 시각 컬럼이 없어 날짜 단위로만 판정한다
@Component
class RoutineReminderRunner(
    private val sessionRepository: WorkoutSessionRepository,
    private val userRepository: UserRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val TITLE = "운동할 시간이에요"
        private const val BODY = "오늘 예약해둔 루틴이 아직 시작 전이에요"
    }

    private val log = LoggerFactory.getLogger(RoutineReminderRunner::class.java)

    fun run(): Int {
        val today = LocalDate.now(APP_ZONE)
        val sessions = sessionRepository.findAllByDateAndStatus(today, SessionStatus.SCHEDULED)
        var hadFailure = false

        sessions.forEach { session ->
            try {
                processSession(session)
            } catch (e: Exception) {
                log.error("루틴 리마인더 발송 실패: sessionId=${session.id}, userId=${session.userId}", e)
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    private fun processSession(session: WorkoutSession) {
        val sessionId = session.id!!
        if (notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, sessionId)) return

        val user = userRepository.findById(session.userId) ?: return
        if (user.status != UserStatus.ACTIVE) return

        val userId = user.id!!
        val settings = settingsRepository.findByUserId(userId)
        if (settings?.routineReminderEnabled == false) return

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, sessionId, TITLE, BODY)
    }
}
