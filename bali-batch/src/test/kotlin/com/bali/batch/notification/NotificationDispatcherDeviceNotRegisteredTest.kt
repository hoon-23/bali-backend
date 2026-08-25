package com.bali.batch.notification

import com.bali.batch.BaliBatchApplication
import com.bali.core.notification.DevicePlatform
import com.bali.core.notification.DeviceToken
import com.bali.core.notification.DeviceTokenRepository
import com.bali.core.notification.NotificationLogRepository
import com.bali.core.notification.NotificationSender
import com.bali.core.notification.NotificationType
import com.bali.core.notification.PushMessage
import com.bali.core.notification.PushSendError
import com.bali.core.notification.PushSendResult
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
class NotificationDispatcherDeviceNotRegisteredTest {

    @Autowired lateinit var dispatcher: NotificationDispatcher
    @Autowired lateinit var deviceTokenRepository: DeviceTokenRepository
    @Autowired lateinit var notificationLogRepository: NotificationLogRepository
    @MockBean lateinit var notificationSender: NotificationSender

    @Test
    fun `DeviceNotRegistered 에러 토큰은 삭제되고 로그는 남기지 않는다`() {
        `when`(notificationSender.send(org.mockito.ArgumentMatchers.anyList())).thenAnswer { invocation ->
            val messages = invocation.getArgument<List<PushMessage>>(0)
            messages.map { PushSendResult(token = it.token, ticketId = null, error = PushSendError.DEVICE_NOT_REGISTERED) }
        }

        val userId = UUID.randomUUID()
        deviceTokenRepository.upsert(DeviceToken(id = null, userId = userId, expoPushToken = "ExponentPushToken[dead-${System.nanoTime()}]", platform = DevicePlatform.IOS, createdAt = Instant.now(), updatedAt = Instant.now()))
        val referenceId = UUID.randomUUID()

        dispatcher.dispatch(userId, NotificationType.ROUTINE_REMINDER, referenceId, "제목", "본문")

        assertFalse(notificationLogRepository.existsByTypeAndReferenceId(NotificationType.ROUTINE_REMINDER, referenceId))
        assertTrue(deviceTokenRepository.findAllByUserId(userId).isEmpty())
    }
}
