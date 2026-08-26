package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.analysis.AnalysisStatus
import com.bali.core.analysis.MonthlyAnalysis
import com.bali.core.analysis.MonthlyAnalysisRepository
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class MonthlySummaryPushRunnerTest {

    @Autowired lateinit var runner: MonthlySummaryPushRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var analysisRepository: MonthlyAnalysisRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "msummary-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-msummary-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun lastCompletedMonthOf(): LocalDate =
        LocalDate.now().withDayOfMonth(1).minusMonths(1)

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `직전 달 분석이 SUCCESS인 유저에게 발송한다`() {
        stubSuccessfulSend()
        val user = newUser()
        val userId = user.id!!
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[ms-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val analysis = analysisRepository.save(MonthlyAnalysis(id = null, userId = userId, monthOf = lastCompletedMonthOf(), status = AnalysisStatus.SUCCESS, summary = com.bali.core.analysis.AnalysisSummary(totalWorkoutMinutes = 240, volumeByExercise = emptyMap(), volumeByMuscleGroup = emptyMap(), cardioTotalMinutes = 0, completionRate = java.math.BigDecimal("1.0"), volumeChangeFromLastWeekPercent = null), insights = emptyList()))

        runner.run()

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.MONTHLY_SUMMARY, analysis.id!!))
    }

    @Test
    fun `직전 달 분석이 NO_ACTIVITY면 발송하지 않는다`() {
        val user = newUser()
        val userId = user.id!!
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[ms2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val analysis = analysisRepository.save(MonthlyAnalysis(id = null, userId = userId, monthOf = lastCompletedMonthOf(), status = AnalysisStatus.NO_ACTIVITY, summary = null, insights = emptyList()))

        runner.run()

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.MONTHLY_SUMMARY, analysis.id!!))
    }
}
