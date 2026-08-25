package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.exercise.ExerciseType
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import com.bali.core.session.SessionLog
import com.bali.core.session.SessionStatus
import com.bali.core.session.WorkoutSession
import com.bali.core.session.WorkoutSessionRepository
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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class InactivityAlertRunnerTest {

    @Autowired lateinit var runner: InactivityAlertRunner
    @Autowired lateinit var userRepository: UserRepository
    @Autowired lateinit var sessionRepository: WorkoutSessionRepository
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    private fun newUser() = userRepository.save(
        User(id = null, email = "inactivity-${System.nanoTime()}@example.com", provider = AuthProvider.GOOGLE, providerId = "sub-inactivity-${System.nanoTime()}", status = UserStatus.ACTIVE, createdAt = Instant.now())
    )

    private fun completedSessionOn(userId: UUID, date: LocalDate) {
        val log = SessionLog.create(ExerciseType.STRENGTH, UUID.randomUUID(), sortOrder = 0, targetSets = 3, targetReps = 10, targetWeight = BigDecimal("40.0")).copy(completed = true)
        val session = WorkoutSession(id = null, userId = userId, date = date, templateId = null, status = SessionStatus.COMPLETED, logs = listOf(log))
        sessionRepository.save(session)
    }

    private fun stubSuccessfulSend() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }
    }

    @Test
    fun `마지막 운동이 7일 이상 지난 유저에게 발송한다`() {
        stubSuccessfulSend()
        val user = newUser()
        val userId = user.id!!
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[ia-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        completedSessionOn(userId, LocalDate.now().minusDays(8))

        runner.run()

        assertTrue(notificationLogRepository.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().minusSeconds(60)))
    }

    @Test
    fun `마지막 운동이 7일 미만이면 발송하지 않는다`() {
        val user = newUser()
        val userId = user.id!!
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[ia2-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        completedSessionOn(userId, LocalDate.now().minusDays(3))

        runner.run()

        assertFalse(notificationLogRepository.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().minusSeconds(60)))
    }

    @Test
    fun `운동 기록이 아예 없는 유저에게는 발송하지 않는다`() {
        val user = newUser()
        val userId = user.id!!
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[ia3-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        runner.run()

        assertFalse(notificationLogRepository.existsByUserIdAndTypeSentAfter(userId, NotificationType.INACTIVITY_ALERT, Instant.now().minusSeconds(60)))
    }
}
