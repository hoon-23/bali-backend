package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@SpringBootTest(classes = [BaliBatchApplication::class])
@Transactional
class NotificationDispatcherTest {

    @Autowired lateinit var dispatcher: NotificationDispatcher
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    @Test
    fun `등록된 토큰이 있으면 발송하고 notification_log에 기록한다`() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
        }

        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[disp-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, referenceId, "제목", "본문")

        assertTrue(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
    }

    @Test
    fun `등록된 토큰이 없으면 아무 것도 하지 않는다`() {
        val userId = UUID.randomUUID()
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, referenceId, "제목", "본문")

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
    }

    // 로그에 title/body가 저장되고 푸시 data에 type/referenceId가 실리는지 확인
    @Test
    fun `발송 시 로그에 title body가 저장되고 푸시 data에 type과 referenceId가 실린다`() {
        val sent = mutableListOf<PushMessage>()
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            sent += messages
            messages.map { PushSendResult(token = it.token, ticketId = "t-${it.token}", error = null) }
        }
        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[data-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.WEEKLY_SUMMARY, referenceId, "제목", "본문")

        assertEquals(mapOf("type" to "WEEKLY_SUMMARY", "referenceId" to referenceId.toString()), sent.single().data)
        val saved = notificationLogRepository.findPageByUserIdSentAfter(userId, Instant.now().minusSeconds(60), 0, 10).items.single()
        assertEquals("제목", saved.title)
        assertEquals("본문", saved.body)
    }

    // referenceId가 없는 알림(미실행 알림)도 data.referenceId가 빈 문자열로 실리는지 확인
    @Test
    fun `referenceId가 null이면 푸시 data의 referenceId는 빈 문자열이다`() {
        val sent = mutableListOf<PushMessage>()
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            sent += messages
            messages.map { PushSendResult(token = it.token, ticketId = "t-${it.token}", error = null) }
        }
        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[null-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))

        dispatcher.dispatch(userId, NotificationType.INACTIVITY_ALERT, null, "제목", "본문")

        assertEquals(mapOf("type" to "INACTIVITY_ALERT", "referenceId" to ""), sent.single().data)
    }
}
