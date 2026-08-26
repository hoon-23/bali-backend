package com.bali.batch.notification

import com.bali.batch.runResiliently
import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneId

// monthly_analysis 배치 직후 실행되어, 방금 SUCCESS로 집계된 직전 달 분석 결과를 유저에게 요약 알림으로 보낸다
@Component
class MonthlySummaryPushRunner(
    private val userRepository: UserRepository,
    private val analysisRepository: MonthlyAnalysisRepository,
    private val settingsRepository: NotificationSettingsRepository,
    private val notificationLogRepository: NotificationLogRepository,
    private val dispatcher: NotificationDispatcher,
) {
    companion object {
        private val APP_ZONE = ZoneId.of("Asia/Seoul")
        private const val TITLE = "이번 달 운동 요약이 도착했어요"
        private const val BODY = "이번 달 운동 기록을 확인해보세요"
    }

    private val log = LoggerFactory.getLogger(MonthlySummaryPushRunner::class.java)

    fun run(): Int {
        val monthOf = LocalDate.now(APP_ZONE).withDayOfMonth(1).minusMonths(1)
        return runResiliently(
            items = userRepository.findAllByStatus(UserStatus.ACTIVE),
            process = { user -> processUser(user, monthOf) },
            onFailure = { user, e -> log.error("월간 요약 알림 발송 실패: userId=${user.id}, monthOf=$monthOf", e) },
        )
    }

    private fun processUser(user: User, monthOf: LocalDate) {
        val userId = user.id!!
        val settings = settingsRepository.findByUserId(userId)
        if (settings?.summaryNotificationEnabled == false) return

        val analysis = analysisRepository.findByUserIdAndMonthOf(userId, monthOf) ?: return
        if (analysis.status != AnalysisStatus.SUCCESS) return

        val analysisId = analysis.id!!
        if (notificationLogRepository.existsByTypeAndReferenceId(NotificationType.MONTHLY_SUMMARY, analysisId)) return

        dispatcher.dispatch(userId, NotificationType.MONTHLY_SUMMARY, analysisId, TITLE, BODY)
    }
}
