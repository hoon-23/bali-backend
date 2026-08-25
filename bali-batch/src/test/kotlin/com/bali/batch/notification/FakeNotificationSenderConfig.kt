package com.bali.batch.notification

import com.bali.core.notification.NotificationSender
import com.bali.core.notification.PushSendResult
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

// 배치 테스트에서 실제 Expo API를 호출하지 않도록 대체하는 fake. 모든 토큰을 발송 성공으로 처리한다
@TestConfiguration
class FakeNotificationSenderConfig {
    @Bean
    @Primary
    fun notificationSender(): NotificationSender = NotificationSender { messages ->
        messages.map { PushSendResult(token = it.token, ticketId = "fake-ticket-${it.token}", error = null) }
    }
}
