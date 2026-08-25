package com.bali.batch.notification

import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.WeeklyAnalysisRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// weekly_analysis 배치 직후 실행되어, 방금 SUCCESS로 집계된 직전 주 분석 결과를 유저에게 요약 알림으로 보낸다
@Component
class WeeklySummaryPushRunner(
    private val userRepository: UserRepository,
    private val analysisRepository: WeeklyAnalysisRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val TITLE = "이번 주 운동 요약이 도착했어요"
        private const val BODY = "이번 주 운동 기록을 확인해보세요"
    }

    private val log = LoggerFactory.getLogger(WeeklySummaryPushRunner::class.java)

    fun run(): Int {
        val weekOf = LocalDate.now(APP_ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        var hadFailure = false

        userRepository.findAllByStatus(UserStatus.ACTIVE).forEach { user ->
            try {
                processUser(user, weekOf)
            } catch (e: Exception) {
                log.error("주간 요약 알림 발송 실패: userId=${user.id}, weekOf=$weekOf", e)
                hadFailure = true
            }
        }
        return if (hadFailure) 1 else 0
    }

    private fun processUser(user: User, weekOf: LocalDate) {
        val userId = user.id!!
        val settings = settingsRepository.findByUserId(userId)
        if (settings?.summaryNotificationEnabled == false) return

        val analysis = analysisRepository.findByUserIdAndWeekOf(userId, weekOf) ?: return
        if (analysis.status != AnalysisStatus.SUCCESS) return

        val analysisId = analysis.id!!
        if (notificationLogRepository.existsByTypeAndReferenceId(NotificationType.WEEKLY_SUMMARY, analysisId)) return

        dispatcher.dispatch(userId, NotificationType.WEEKLY_SUMMARY, analysisId, TITLE, BODY)
    }
}
