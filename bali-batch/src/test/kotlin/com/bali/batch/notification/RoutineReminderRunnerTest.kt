package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationSettings
import com.bali.core.notification.NotificationSettingsRepository
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
import com.bali.core.user.AuthProvider
import com.bali.core.user.User
import com.bali.core.user.UserRepository
import com.bali.core.user.UserStatus
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
class RoutineReminderRunnerTest {

    @Autowired lateinit var runner: RoutineReminderRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var settingsRepository: NotificationSettingsRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "reminder-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-reminder-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `오늘 SCHEDULED 세션이 있고 토큰이 등록된 유저에게 발송하고 로그를 남긴다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[rr-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val session = sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        val exitCode = runner.run()

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!))
        assertTrue(exitCode == 0)
    }

    @Test
    fun `이미 발송한 세션은 다시 발송하지 않는다`() {
        stubSuccessfulSend()
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[rr2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val session = sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        runner.run()
        val firstLogCount = notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!)
        runner.run()

        assertTrue(firstLogCount)
        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!))
    }

    @Test
    fun `routine_reminder_enabled가 false인 유저에게는 발송하지 않는다`() {
        val user = newUser()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = user.id!!, expoPushToken = "ExponentPushToken[rr3-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        settingsRepository.save(NotificationSettings(userId = user.id!!, routineReminderEnabled = false))
        val session = sessionRepository.save(WorkoutSession(id = null, userId = user.id!!, date = LocalDate.now(), templateId = null, status = SessionStatus.SCHEDULED, logs = emptyList()))

        runner.run()

        assertTrue(!notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, session.id!!))
    }
}
